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

/**
 * Global filter: extracts the auth token from the cookie and stores it in the request context.
 * Aborts with 401 if no token is present.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class TokenAuthFilter implements ContainerRequestFilter {

  @Override
  public void filter(ContainerRequestContext ctx) {
    String token = extractToken(ctx);
    if (token == null || token.isBlank()) {
      ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
      return;
    }
    ctx.setProperty(AUTH_TOKEN_KEY, token);
  }

  private String extractToken(ContainerRequestContext ctx) {
    Cookie cookie = ctx.getCookies().get(AUTH_TOKEN_KEY);
    return cookie != null ? cookie.getValue() : null;
  }

  /**
   * Secondary filter bound to {@link RequiresTokenValidation}: pre-validates the token via
   * the service layer. Endpoints without this annotation (e.g. /myself) skip pre-validation.
   */
  @Provider
  @RequiresTokenValidation
  @Priority(Priorities.AUTHENTICATION + 1)
  public static class TokenValidationFilter implements ContainerRequestFilter {

    private final UserService userService;

    @Inject
    public TokenValidationFilter(UserService userService) {
      this.userService = userService;
    }

    @Override
    public void filter(ContainerRequestContext ctx) {
      String token = (String) ctx.getProperty(AUTH_TOKEN_KEY);
      if (token == null || userService.getUserMyself(token).isEmpty()) {
        ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
      }
    }
  }
}
