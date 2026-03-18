// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.controllers;

import com.google.inject.Inject;
import com.zextras.carbonio.user_management.generated.AccountApiService;
import com.zextras.carbonio.user_management.services.AccountLdapService;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

public class AccountApiController implements AccountApiService {

  private final AccountLdapService accountLdapService;

  @Inject
  public AccountApiController(AccountLdapService accountLdapService) {
    this.accountLdapService = accountLdapService;
  }

  @Override
  public Response getAccountInfo(String accountId, SecurityContext securityContext) {
    try {
      return accountLdapService.getCosAndDomainByAccountId(accountId)
          .map(result -> Response.ok().entity(result).build())
          .orElse(Response.status(Response.Status.NOT_FOUND).build());
    } catch (RuntimeException e) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }
}
