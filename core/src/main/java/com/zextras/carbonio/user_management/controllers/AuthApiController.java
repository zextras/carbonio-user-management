// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.controllers;

import com.google.inject.Inject;
import com.zextras.carbonio.user_management.generated.AuthApiService;
import com.zextras.carbonio.user_management.services.UserService;
import javax.enterprise.context.RequestScoped;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

@RequestScoped
@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen", date = "2021-12-22T09:50:40.665594+01:00[Europe/Rome]")
public class AuthApiController implements AuthApiService {

  private final UserService userService;

  @Inject
  public AuthApiController(UserService userService) {
    this.userService = userService;
  }

  public Response authToken(
    String tokenId,
    SecurityContext securityContext
  ) {
    return userService.validateUserToken(tokenId);
  }
}
