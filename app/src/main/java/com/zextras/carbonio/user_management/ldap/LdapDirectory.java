package com.zextras.carbonio.user_management.ldap;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPInterface;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;
import com.zextras.carbonio.quarkus.extensions.bootstrap.ApplicationConfigService;
import com.zextras.carbonio.user_management.UserManagementServiceConfig.ApplicationConfig;
import com.zextras.carbonio.user_management.UserManagementServiceConfig.ZimbraAttributes;
import com.zextras.carbonio.user_management.UserManagementServiceConfig.ZimbraObjectClasses;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.IDN;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

@ApplicationScoped
public class LdapDirectory {

  static final String CONFIG_DN = "cn=config,cn=zimbra";
  static final String GLOBAL_GRANT_DN = "cn=globalgrant,cn=zimbra";
  static final Duration SHARED_ENTRY_TTL = Duration.ofSeconds(60);

  private final LDAPInterface ldap;
  private final String baseDn;
  private final Cache<String, Optional<Entry>> sharedEntries;

  @Inject
  public LdapDirectory(
      LDAPConnectionPool pool, ApplicationConfigService configService, Ticker ticker) {
    this(pool, configService.get(ApplicationConfig.LDAP_BASE_DN).orElse(""), ticker);
  }

  public LdapDirectory(LDAPInterface ldap, String baseDn, Ticker ticker) {
    this.ldap = ldap;
    this.baseDn = baseDn;
    this.sharedEntries =
        Caffeine.newBuilder()
            .expireAfterWrite(SHARED_ENTRY_TTL)
            .maximumSize(5_000)
            .ticker(ticker)
            .build();
  }

  public Optional<Entry> accountById(String id) {
    return searchOne(
        Filter.createANDFilter(
            Filter.createEqualityFilter(ZimbraAttributes.ID, id), accountClass()));
  }

  public List<Entry> accountsByIds(Collection<String> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    Filter anyId =
        Filter.createORFilter(
            ids.stream().map(id -> Filter.createEqualityFilter(ZimbraAttributes.ID, id)).toList());
    return search(baseDn, Filter.createANDFilter(anyId, accountClass()));
  }

  public Optional<Entry> accountByEmail(String email) {
    String address = fixupAddress(email);
    Optional<Entry> account = accountByAddress(address);
    if (account.isPresent()) {
      return account;
    }
    return addressInAliasTarget(address).flatMap(this::accountByAddress);
  }

  public List<Entry> distributionListsWithMember(Collection<String> addresses) {
    if (addresses.isEmpty()) {
      return List.of();
    }
    Filter anyAddress =
        Filter.createORFilter(
            addresses.stream()
                .map(a -> Filter.createEqualityFilter(ZimbraAttributes.MAIL_FORWARDING_ADDRESS, a))
                .toList());
    return search(
        baseDn,
        Filter.createANDFilter(
            Filter.createEqualityFilter(
                ZimbraObjectClasses.OBJECT_CLASS, ZimbraObjectClasses.DISTRIBUTION_LIST),
            anyAddress));
  }

  public List<Entry> dynamicGroupsByIds(Collection<String> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    Filter anyId =
        Filter.createORFilter(
            ids.stream().map(id -> Filter.createEqualityFilter(ZimbraAttributes.ID, id)).toList());
    return search(
        baseDn,
        Filter.createANDFilter(
            Filter.createEqualityFilter(
                ZimbraObjectClasses.OBJECT_CLASS, ZimbraObjectClasses.GROUP),
            anyId));
  }

  public Optional<Entry> domainByName(String name) {
    return domainByName(name, Read.CACHED);
  }

  public Optional<Entry> domainByName(String name, Read read) {
    String ascii = toAsciiDomain(name);
    return shared(
        "domain:name:" + ascii,
        () ->
            searchOne(
                Filter.createANDFilter(
                    Filter.createEqualityFilter(ZimbraAttributes.DOMAIN_NAME, ascii),
                    Filter.createEqualityFilter(
                        ZimbraObjectClasses.OBJECT_CLASS, ZimbraObjectClasses.DOMAIN))), read);
  }

  public Optional<Entry> domainById(String id) {
    return domainById(id, Read.CACHED);
  }

  public Optional<Entry> domainById(String id, Read read) {
    return shared(
        "domain:id:" + id,
        () ->
            searchOne(
                Filter.createANDFilter(
                    Filter.createEqualityFilter(ZimbraAttributes.ID, id),
                    Filter.createEqualityFilter(
                        ZimbraObjectClasses.OBJECT_CLASS, ZimbraObjectClasses.DOMAIN))), read);
  }

  public Optional<Entry> cosById(String id) {
    return cosById(id, Read.CACHED);
  }

  public Optional<Entry> cosById(String id, Read read) {
    return shared(
        "cos:id:" + id,
        () ->
            searchOne(
                Filter.createANDFilter(
                    Filter.createEqualityFilter(ZimbraAttributes.ID, id),
                    Filter.createEqualityFilter(
                        ZimbraObjectClasses.OBJECT_CLASS, ZimbraObjectClasses.COS))), read);
  }

  public Optional<Entry> cosByName(String name) {
    return cosByName(name, Read.CACHED);
  }

  public Optional<Entry> cosByName(String name, Read read) {
    return shared(
        "cos:name:" + name.toLowerCase(Locale.ROOT),
        () ->
            searchOne(
                Filter.createANDFilter(
                    Filter.createEqualityFilter(ZimbraAttributes.COMMON_NAME, name),
                    Filter.createEqualityFilter(
                        ZimbraObjectClasses.OBJECT_CLASS, ZimbraObjectClasses.COS))), read);
  }

  public Optional<Entry> serverByName(String name) {
    return shared(
        "server:" + name.toLowerCase(Locale.ROOT),
        () ->
            searchOne(
                Filter.createANDFilter(
                    Filter.createEqualityFilter(ZimbraAttributes.COMMON_NAME, name),
                    Filter.createEqualityFilter(
                        ZimbraObjectClasses.OBJECT_CLASS, ZimbraObjectClasses.SERVER))));
  }

  public Entry config() {
    return shared("config", this::readConfig)
        .orElseThrow(() -> new DirectoryException("Global config entry not found", null));
  }

  public Entry freshConfig() {
    Optional<Entry> config = readConfig();
    sharedEntries.put("config", config);
    return config.orElseThrow(() -> new DirectoryException("Global config entry not found", null));
  }

  public Optional<Entry> globalGrant(Read read) {
    return shared("globalgrant", () -> entryByDn(GLOBAL_GRANT_DN), read);
  }

  private Optional<Entry> readConfig() {
    return entryByDn(CONFIG_DN);
  }

  private Optional<Entry> accountByAddress(String address) {
    Filter byAddress =
        Filter.createORFilter(
            Filter.createEqualityFilter(ZimbraAttributes.MAIL_DELIVERY_ADDRESS, address),
            Filter.createEqualityFilter(ZimbraAttributes.MAIL_ALIAS, address),
            Filter.createEqualityFilter(ZimbraAttributes.OLD_MAIL_ADDRESS, address));
    return searchOne(Filter.createANDFilter(byAddress, accountClass()));
  }

  private Optional<String> addressInAliasTarget(String address) {
    int at = address.indexOf('@');
    if (at < 0 || address.indexOf('@', at + 1) >= 0) {
      return Optional.empty();
    }
    return domainByName(address.substring(at + 1))
        .filter(domain -> !"local".equalsIgnoreCase(domainType(domain)))
        .map(domain -> domain.getAttributeValue(ZimbraAttributes.DOMAIN_ALIAS_TARGET_ID))
        .flatMap(this::domainById)
        .map(target -> target.getAttributeValue(ZimbraAttributes.DOMAIN_NAME))
        .map(targetName -> address.substring(0, at) + "@" + targetName);
  }

  private static String domainType(Entry domain) {
    String type = domain.getAttributeValue(ZimbraAttributes.DOMAIN_TYPE);
    return type == null ? "local" : type;
  }

  private String fixupAddress(String email) {
    String address = email.trim();
    int at = address.indexOf('@');
    if (at < 0) {
      String defaultDomain = config().getAttributeValue(ZimbraAttributes.DEFAULT_DOMAIN_NAME);
      if (defaultDomain == null) {
        throw new IllegalArgumentException("No domain in address and no default domain");
      }
      return address + "@" + defaultDomain;
    }
    return address.substring(0, at + 1) + toAsciiDomain(address.substring(at + 1));
  }

  private static String toAsciiDomain(String domain) {
    try {
      return IDN.toASCII(domain);
    } catch (IllegalArgumentException e) {
      return domain;
    }
  }

  private static Filter accountClass() {
    return Filter.createEqualityFilter(
        ZimbraObjectClasses.OBJECT_CLASS, ZimbraObjectClasses.ACCOUNT);
  }

  public enum Read {
    CACHED,
    FRESH
  }

  private Optional<Entry> shared(String key, Supplier<Optional<Entry>> loader) {
    return shared(key, loader, Read.CACHED);
  }

  private Optional<Entry> shared(String key, Supplier<Optional<Entry>> loader, Read read) {
    if (read == Read.CACHED) {
      Optional<Entry> cached = sharedEntries.getIfPresent(key);
      if (cached != null) {
        return cached;
      }
    }
    Optional<Entry> loaded = loader.get();
    sharedEntries.put(key, loaded);
    return loaded;
  }

  private Optional<Entry> entryByDn(String dn) {
    try {
      return Optional.ofNullable(ldap.getEntry(dn, "*"));
    } catch (LDAPException e) {
      throw new DirectoryException("LDAP read failed for " + dn, e);
    }
  }

  private Optional<Entry> searchOne(Filter filter) {
    return search(baseDn, filter).stream().findFirst();
  }

  private List<Entry> search(String base, Filter filter) {
    try {
      return ldap
          .search(new SearchRequest(base, SearchScope.SUB, filter, "*"))
          .getSearchEntries()
          .stream()
          .map(SearchResultEntry.class::cast)
          .map(Entry.class::cast)
          .toList();
    } catch (LDAPException e) {
      throw new DirectoryException("LDAP search failed: " + filter, e);
    }
  }
}
