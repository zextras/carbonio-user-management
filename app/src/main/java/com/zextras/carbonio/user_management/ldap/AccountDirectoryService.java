package com.zextras.carbonio.user_management.ldap;

import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPInterface;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;
import com.zextras.carbonio.quarkus.extensions.bootstrap.ApplicationConfigService;
import com.zextras.carbonio.user_management.UserManagementServiceConfig.ApplicationConfig;
import com.zextras.carbonio.user_management.UserManagementServiceConfig.ZimbraAttributes;
import com.zextras.carbonio.user_management.UserManagementServiceConfig.ZimbraObjectClasses;
import com.zextras.carbonio.user_management.record.AccountDirectory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class AccountDirectoryService {

  private static final Logger logger = LoggerFactory.getLogger(AccountDirectoryService.class);

  private final LDAPInterface ldap;
  private final String baseDn;

  @Inject
  public AccountDirectoryService(LDAPConnectionPool pool, ApplicationConfigService configService) {
    this(pool, configService.get(ApplicationConfig.LDAP_BASE_DN).orElse(""));
  }

  public AccountDirectoryService(LDAPInterface ldap, String baseDn) {
    this.ldap = ldap;
    this.baseDn = baseDn;
  }

  public Optional<AccountDirectory> findByAccountId(String accountId) {
    try {
      Optional<SearchResultEntry> account =
          searchOne(
              ZimbraObjectClasses.ACCOUNT,
              ZimbraAttributes.ID,
              accountId,
              ZimbraAttributes.COS_ID,
              ZimbraAttributes.MAIL);
      if (account.isEmpty()) {
        return Optional.empty();
      }

      String mail = account.get().getAttributeValue(ZimbraAttributes.MAIL);
      if (mail == null || mail.indexOf('@') < 0) {
        logger.warn("Account {} has no usable mail attribute", accountId);
        return Optional.empty();
      }
      String domainName = mail.substring(mail.indexOf('@') + 1);

      Optional<SearchResultEntry> domain =
          searchOne(
              ZimbraObjectClasses.DOMAIN,
              ZimbraAttributes.DOMAIN_NAME,
              domainName,
              ZimbraAttributes.ID,
              ZimbraAttributes.DOMAIN_DEFAULT_COS_ID);
      if (domain.isEmpty()) {
        logger.warn("Domain {} not found for account {}", domainName, accountId);
        return Optional.empty();
      }

      String cosId = account.get().getAttributeValue(ZimbraAttributes.COS_ID);
      if (cosId == null) {
        cosId = domain.get().getAttributeValue(ZimbraAttributes.DOMAIN_DEFAULT_COS_ID);
      }
      if (cosId == null) {
        cosId =
            searchOne(
                    ZimbraObjectClasses.COS, ZimbraAttributes.COMMON_NAME,
                    ZimbraObjectClasses.DEFAULT_COS_NAME, ZimbraAttributes.ID)
                .map(entry -> entry.getAttributeValue(ZimbraAttributes.ID))
                .orElse(null);
      }

      return Optional.of(
          new AccountDirectory(
              accountId, cosId, domain.get().getAttributeValue(ZimbraAttributes.ID)));
    } catch (LDAPException e) {
      throw new DirectoryException("LDAP query failed for account " + accountId, e);
    }
  }

  private Optional<SearchResultEntry> searchOne(
      String objectClass, String attribute, String value, String... returned) throws LDAPException {
    Filter filter =
        Filter.createANDFilter(
            Filter.createEqualityFilter(attribute, value),
            Filter.createEqualityFilter(ZimbraObjectClasses.OBJECT_CLASS, objectClass));
    SearchResult result = ldap.search(new SearchRequest(baseDn, SearchScope.SUB, filter, returned));
    return result.getSearchEntries().stream().findFirst();
  }
}
