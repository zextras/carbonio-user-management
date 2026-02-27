// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.rest;

import static com.zextras.carbonio.user_management.UserManagementServiceConfig.AUTH_TOKEN_KEY;

import com.zextras.carbonio.user_management.service.UserService;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class TokenAuthFilter implements ContainerRequestFilter {

  private final UserService userService;

  @Inject
  public TokenAuthFilter(UserService userService) {
    this.userService = userService;
  }

  @Override
  public void filter(ContainerRequestContext ctx) {
    String token = extractToken(ctx);
    if (token == null || token.isBlank()) {
      ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
      return;
    }

    // /users/myself doesn't need pre-auth: the service validates the token itself
    if (isMyselfPath(ctx)) {
      ctx.setProperty(AUTH_TOKEN_KEY, token);
      return;
    }

    // For other endpoints: validate token via service (cache-first internally)
    if (userService.getUserMyself(token).isEmpty()) {
      ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
      return;
    }

    ctx.setProperty(AUTH_TOKEN_KEY, token);
  }

  private String extractToken(ContainerRequestContext ctx) {
    Cookie cookie = ctx.getCookies().get(AUTH_TOKEN_KEY);
    return cookie != null ? cookie.getValue() : null;
  }

  private boolean isMyselfPath(ContainerRequestContext ctx) {
    return ctx.getUriInfo().getPath().endsWith("/myself");
  }
}
