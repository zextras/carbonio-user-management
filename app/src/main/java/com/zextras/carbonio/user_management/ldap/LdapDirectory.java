package com.zextras.carbonio.user_management.ldap;

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
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@ApplicationScoped
public class LdapDirectory {

  static final String CONFIG_DN = "cn=config,cn=zimbra";
  static final String GLOBAL_GRANT_DN = "cn=globalgrant,cn=zimbra";

  private final LDAPInterface ldap;
  private final String baseDn;

  @Inject
  public LdapDirectory(LDAPConnectionPool pool, ApplicationConfigService configService) {
    this(pool, configService.get(ApplicationConfig.LDAP_BASE_DN).orElse(""));
  }

  public LdapDirectory(LDAPInterface ldap, String baseDn) {
    this.ldap = ldap;
    this.baseDn = baseDn;
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
    String ascii = toAsciiDomain(name);
    return searchOne(
        Filter.createANDFilter(
            Filter.createEqualityFilter(ZimbraAttributes.DOMAIN_NAME, ascii),
            Filter.createEqualityFilter(
                ZimbraObjectClasses.OBJECT_CLASS, ZimbraObjectClasses.DOMAIN)));
  }

  public Optional<Entry> domainById(String id) {
    return searchOne(
        Filter.createANDFilter(
            Filter.createEqualityFilter(ZimbraAttributes.ID, id),
            Filter.createEqualityFilter(
                ZimbraObjectClasses.OBJECT_CLASS, ZimbraObjectClasses.DOMAIN)));
  }

  public Optional<Entry> cosById(String id) {
    return searchOne(
        Filter.createANDFilter(
            Filter.createEqualityFilter(ZimbraAttributes.ID, id),
            Filter.createEqualityFilter(
                ZimbraObjectClasses.OBJECT_CLASS, ZimbraObjectClasses.COS)));
  }

  public Optional<Entry> cosByName(String name) {
    return searchOne(
        Filter.createANDFilter(
            Filter.createEqualityFilter(ZimbraAttributes.COMMON_NAME, name),
            Filter.createEqualityFilter(
                ZimbraObjectClasses.OBJECT_CLASS, ZimbraObjectClasses.COS)));
  }

  public Optional<Entry> serverByName(String name) {
    return searchOne(
        Filter.createANDFilter(
            Filter.createEqualityFilter(ZimbraAttributes.COMMON_NAME, name),
            Filter.createEqualityFilter(
                ZimbraObjectClasses.OBJECT_CLASS, ZimbraObjectClasses.SERVER)));
  }

  public Entry config() {
    return entryByDn(CONFIG_DN)
        .orElseThrow(() -> new DirectoryException("Global config entry not found", null));
  }

  public Optional<Entry> globalGrant() {
    return entryByDn(GLOBAL_GRANT_DN);
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

  public static String normalizeAddress(String address) {
    String trimmed = address.trim();
    int at = trimmed.lastIndexOf('@');
    String normalized =
        at < 0
            ? trimmed
            : trimmed.substring(0, at + 1) + toAsciiDomain(trimmed.substring(at + 1));
    return normalized.toLowerCase(Locale.ROOT);
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
