// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.rest;

import static com.zextras.carbonio.user_management.UserManagementServiceConfig.AUTH_TOKEN_KEY;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Filter bound to {@link RequiresToken}: extracts the auth token from the {@code ZM_AUTH_TOKEN}
 * cookie and stores it in the request context, aborting with 401 if it is missing or blank.
 *
 * <p>Only {@code myself} carries {@link RequiresToken}. There the token is not an authorization
 * gate but the functional identity input that {@code getUserMyself} forwards to mailbox to
 * resolve "who am I". The by-id/by-email/batch lookups are trusted forwards to mailbox's
 * internal API — service-to-service authorization is handled by mailbox via the Consul mesh —
 * so they are not bound to this filter and neither require nor read any token.
 */
@Provider
@RequiresToken
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
}
