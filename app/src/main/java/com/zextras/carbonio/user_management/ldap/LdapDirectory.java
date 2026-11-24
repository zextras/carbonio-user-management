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
import java.util.stream.Stream;

@ApplicationScoped
public class LdapDirectory {

  static final String CONFIG_DN = "cn=config,cn=zimbra";
  static final String GLOBAL_GRANT_DN = "cn=globalgrant,cn=zimbra";
  static final List<String> CONFIG_ATTRIBUTES =
      List.of(
          ZimbraAttributes.DEFAULT_DOMAIN_NAME,
          ZimbraAttributes.LOCALE,
          ZimbraAttributes.PUBLIC_SERVICE_HOSTNAME,
          ZimbraAttributes.PUBLIC_SERVICE_PROTOCOL,
          ZimbraAttributes.AUTH_TOKEN_KEY,
          ZimbraAttributes.AUTH_TOKEN_VALIDITY_VALUE_ENABLED);
  static final List<String> DOMAIN_ATTRIBUTES =
      List.of(
          ZimbraAttributes.ID,
          ZimbraAttributes.DOMAIN_NAME,
          ZimbraAttributes.DOMAIN_TYPE,
          ZimbraAttributes.DOMAIN_STATUS,
          ZimbraAttributes.DOMAIN_ALIAS_TARGET_ID,
          ZimbraAttributes.DOMAIN_DEFAULT_COS_ID,
          ZimbraAttributes.DOMAIN_DEFAULT_EXTERNAL_COS_ID,
          ZimbraAttributes.PUBLIC_SERVICE_HOSTNAME,
          ZimbraAttributes.PUBLIC_SERVICE_PROTOCOL,
          ZimbraAttributes.LOCALE,
          ZimbraAttributes.ACE);
  static final List<String> SERVER_ATTRIBUTES = List.of(ZimbraAttributes.LOCALE);
  static final List<String> GROUP_ATTRIBUTES =
      List.of(
          ZimbraAttributes.ID,
          ZimbraAttributes.MAIL,
          ZimbraAttributes.MAIL_DELIVERY_ADDRESS,
          ZimbraAttributes.MAIL_ALIAS,
          ZimbraAttributes.IS_ADMIN_GROUP,
          ZimbraAttributes.ACE);
  static final List<String> GLOBAL_GRANT_ATTRIBUTES = List.of(ZimbraAttributes.ACE);
  public static final List<String> ACCOUNT_INFO_ATTRIBUTES =
      List.of(
          ZimbraAttributes.ID,
          ZimbraAttributes.MAIL_DELIVERY_ADDRESS,
          ZimbraAttributes.DISPLAY_NAME,
          ZimbraAttributes.ACCOUNT_STATUS,
          ZimbraAttributes.IS_EXTERNAL_VIRTUAL_ACCOUNT,
          ZimbraAttributes.COS_ID,
          ZimbraAttributes.IS_ADMIN_ACCOUNT);
  public static final List<String> ACCOUNT_MYSELF_ATTRIBUTES =
      concat(
          ACCOUNT_INFO_ATTRIBUTES,
          List.of(ZimbraAttributes.PREF_LOCALE, ZimbraAttributes.LOCALE, ZimbraAttributes.MAIL_HOST),
          UserMapper.SETTING_ATTRIBUTES);
  public static final List<String> ACCOUNT_RIGHTS_ATTRIBUTES =
      List.of(
          ZimbraAttributes.ID,
          ZimbraAttributes.MAIL_DELIVERY_ADDRESS,
          ZimbraAttributes.MAIL_ALIAS,
          ZimbraAttributes.MEMBER_OF,
          ZimbraAttributes.IS_ADMIN_ACCOUNT,
          ZimbraAttributes.IS_DELEGATED_ADMIN_ACCOUNT,
          ZimbraAttributes.ACE);
  public static final List<String> ACCOUNT_TOKEN_ATTRIBUTES =
      List.of(ZimbraAttributes.AUTH_TOKENS, ZimbraAttributes.AUTH_TOKEN_VALIDITY_VALUE);
  static final List<String> COS_ID_ATTRIBUTES = List.of(ZimbraAttributes.ID);
  static final List<String> COS_MYSELF_ATTRIBUTES =
      concat(
          List.of(ZimbraAttributes.ID, ZimbraAttributes.PREF_LOCALE, ZimbraAttributes.LOCALE),
          UserMapper.SETTING_ATTRIBUTES);
  public static final List<String> COS_RIGHTS_ATTRIBUTES =
      List.of(ZimbraAttributes.ID, ZimbraAttributes.ACE);

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

  public Optional<Entry> accountById(String id, List<String> attributes) {
    return searchOne(
        Filter.createANDFilter(
            Filter.createEqualityFilter(ZimbraAttributes.ID, id), accountClass()),
        attributes);
  }

  public List<Entry> accountsByIds(Collection<String> ids, List<String> attributes) {
    if (ids.isEmpty()) {
      return List.of();
    }
    Filter anyId =
        Filter.createORFilter(
            ids.stream().map(id -> Filter.createEqualityFilter(ZimbraAttributes.ID, id)).toList());
    return search(Filter.createANDFilter(anyId, accountClass()), attributes);
  }

  public Optional<Entry> accountByEmail(String email, List<String> attributes) {
    String address = fixupAddress(email);
    Optional<Entry> account = accountByAddress(address, attributes);
    if (account.isPresent()) {
      return account;
    }
    return addressInAliasTarget(address).flatMap(alias -> accountByAddress(alias, attributes));
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
        Filter.createANDFilter(
            Filter.createEqualityFilter(
                ZimbraObjectClasses.OBJECT_CLASS, ZimbraObjectClasses.DISTRIBUTION_LIST),
            anyAddress),
        GROUP_ATTRIBUTES);
  }

  public List<Entry> dynamicGroupsByIds(Collection<String> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    Filter anyId =
        Filter.createORFilter(
            ids.stream().map(id -> Filter.createEqualityFilter(ZimbraAttributes.ID, id)).toList());
    return search(
        Filter.createANDFilter(
            Filter.createEqualityFilter(
                ZimbraObjectClasses.OBJECT_CLASS, ZimbraObjectClasses.GROUP),
            anyId),
        GROUP_ATTRIBUTES);
  }

  public Optional<Entry> domainByName(String name) {
    String ascii = toAsciiDomain(name);
    return searchOne(
        Filter.createANDFilter(
            Filter.createEqualityFilter(ZimbraAttributes.DOMAIN_NAME, ascii),
            Filter.createEqualityFilter(
                ZimbraObjectClasses.OBJECT_CLASS, ZimbraObjectClasses.DOMAIN)),
        DOMAIN_ATTRIBUTES);
  }

  public Optional<Entry> domainById(String id) {
    return searchOne(
        Filter.createANDFilter(
            Filter.createEqualityFilter(ZimbraAttributes.ID, id),
            Filter.createEqualityFilter(
                ZimbraObjectClasses.OBJECT_CLASS, ZimbraObjectClasses.DOMAIN)),
        DOMAIN_ATTRIBUTES);
  }

  public Optional<Entry> cosById(String id, List<String> attributes) {
    return searchOne(
        Filter.createANDFilter(
            Filter.createEqualityFilter(ZimbraAttributes.ID, id),
            Filter.createEqualityFilter(ZimbraObjectClasses.OBJECT_CLASS, ZimbraObjectClasses.COS)),
        attributes);
  }

  public Optional<Entry> cosByName(String name, List<String> attributes) {
    return searchOne(
        Filter.createANDFilter(
            Filter.createEqualityFilter(ZimbraAttributes.COMMON_NAME, name),
            Filter.createEqualityFilter(ZimbraObjectClasses.OBJECT_CLASS, ZimbraObjectClasses.COS)),
        attributes);
  }

  public Optional<Entry> serverByName(String name) {
    return searchOne(
        Filter.createANDFilter(
            Filter.createEqualityFilter(ZimbraAttributes.COMMON_NAME, name),
            Filter.createEqualityFilter(
                ZimbraObjectClasses.OBJECT_CLASS, ZimbraObjectClasses.SERVER)),
        SERVER_ATTRIBUTES);
  }

  public Entry config() {
    return entryByDn(CONFIG_DN, CONFIG_ATTRIBUTES)
        .orElseThrow(() -> new DirectoryException("Global config entry not found", null));
  }

  public Optional<Entry> globalGrant() {
    return entryByDn(GLOBAL_GRANT_DN, GLOBAL_GRANT_ATTRIBUTES);
  }

  private Optional<Entry> accountByAddress(String address, List<String> attributes) {
    Filter byAddress =
        Filter.createORFilter(
            Filter.createEqualityFilter(ZimbraAttributes.MAIL_DELIVERY_ADDRESS, address),
            Filter.createEqualityFilter(ZimbraAttributes.MAIL_ALIAS, address),
            Filter.createEqualityFilter(ZimbraAttributes.OLD_MAIL_ADDRESS, address));
    return searchOne(Filter.createANDFilter(byAddress, accountClass()), attributes);
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
        at < 0 ? trimmed : trimmed.substring(0, at + 1) + toAsciiDomain(trimmed.substring(at + 1));
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

  private Optional<Entry> entryByDn(String dn, List<String> attributes) {
    try {
      return Optional.ofNullable(ldap.getEntry(dn, attributes.toArray(String[]::new)));
    } catch (LDAPException e) {
      throw new DirectoryException("LDAP read failed for " + dn, e);
    }
  }

  private Optional<Entry> searchOne(Filter filter, List<String> attributes) {
    return search(filter, attributes).stream().findFirst();
  }

  private List<Entry> search(Filter filter, List<String> attributes) {
    try {
      return ldap
          .search(
              new SearchRequest(
                  baseDn, SearchScope.SUB, filter, attributes.toArray(String[]::new)))
          .getSearchEntries()
          .stream()
          .map(SearchResultEntry.class::cast)
          .map(Entry.class::cast)
          .toList();
    } catch (LDAPException e) {
      throw new DirectoryException("LDAP search failed: " + filter, e);
    }
  }

  @SafeVarargs
  static List<String> concat(List<String>... lists) {
    return Stream.of(lists).flatMap(List::stream).distinct().toList();
  }
}
