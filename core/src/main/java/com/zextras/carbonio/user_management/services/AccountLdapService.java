// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.services;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;
import com.zextras.carbonio.user_management.generated.model.AccountInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@Singleton
public class AccountLdapService {

  private static final Logger logger = LoggerFactory.getLogger(AccountLdapService.class);

  private final LDAPConnectionPool connectionPool;
  private final String baseDn;

  @Inject
  public AccountLdapService(LDAPConnectionPool connectionPool, @Named("ldapBaseDn") String baseDn) {
    this.connectionPool = connectionPool;
    this.baseDn = baseDn;
  }

  public Optional<AccountInfo> getCosAndDomainByAccountId(String accountId) {
    try {
      // Step 1: lookup account by zimbraId
      Filter accountFilter = Filter.createANDFilter(
          Filter.createEqualityFilter("zimbraId", accountId),
          Filter.createEqualityFilter("objectClass", "zimbraAccount")
      );

      SearchRequest accountSearch = new SearchRequest(
          baseDn, SearchScope.SUB, accountFilter, "zimbraCOSId", "mail"
      );

      SearchResult accountResult = connectionPool.search(accountSearch);
      if (accountResult.getEntryCount() == 0) {
        return Optional.empty();
      }

      SearchResultEntry accountEntry = accountResult.getSearchEntries().get(0);
      String cosId = accountEntry.getAttributeValue("zimbraCOSId");
      String mail = accountEntry.getAttributeValue("mail");

      if (mail == null) {
        logger.warn("Account {} has no mail attribute", accountId);
        return Optional.empty();
      }

      // Extract domain name from email
      String domainName = mail.substring(mail.indexOf('@') + 1);

      // Step 2: lookup domain by name
      Filter domainFilter = Filter.createANDFilter(
          Filter.createEqualityFilter("zimbraDomainName", domainName),
          Filter.createEqualityFilter("objectClass", "zimbraDomain")
      );

      SearchRequest domainSearch = new SearchRequest(
          baseDn, SearchScope.SUB, domainFilter, "zimbraId", "zimbraDomainDefaultCOSId"
      );

      SearchResult domainResult = connectionPool.search(domainSearch);
      if (domainResult.getEntryCount() == 0) {
        logger.warn("Domain {} not found for account {}", domainName, accountId);
        return Optional.empty();
      }

      SearchResultEntry domainEntry = domainResult.getSearchEntries().get(0);
      String domainId = domainEntry.getAttributeValue("zimbraId");

      // COS fallback: if not set on account, use domain default
      if (cosId == null) {
        cosId = domainEntry.getAttributeValue("zimbraDomainDefaultCOSId");
      }

      // COS fallback: if still not set, lookup the "default" COS
      if (cosId == null) {
        Filter defaultCosFilter = Filter.createANDFilter(
            Filter.createEqualityFilter("cn", "default"),
            Filter.createEqualityFilter("objectClass", "zimbraCOS")
        );
        SearchRequest cosSearch = new SearchRequest(
            baseDn, SearchScope.SUB, defaultCosFilter, "zimbraId"
        );
        SearchResult cosResult = connectionPool.search(cosSearch);
        if (cosResult.getEntryCount() > 0) {
          cosId = cosResult.getSearchEntries().get(0).getAttributeValue("zimbraId");
        }
      }

      AccountInfo accountInfo = new AccountInfo();
      accountInfo.setAccountId(accountId);
      accountInfo.setCosId(cosId);
      accountInfo.setDomainId(domainId);
      return Optional.of(accountInfo);

    } catch (LDAPException e) {
      logger.error("LDAP query failed for account {}: {}", accountId, e.getMessage());
      throw new RuntimeException("LDAP query failed", e);
    }
  }
}
