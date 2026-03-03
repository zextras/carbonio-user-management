// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.rest;

import static com.zextras.carbonio.user_management.UserManagementServiceConfig.AUTH_TOKEN_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zextras.carbonio.user_management.record.UserMyself;
import com.zextras.carbonio.user_management.service.UserService;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TokenAuthFilterTest {

  private TokenAuthFilter filter;
  private ContainerRequestContext ctx;

  @BeforeEach
  void setUp() {
    filter = new TokenAuthFilter();
    ctx = mock(ContainerRequestContext.class);
  }

  private void setCookie(String token) {
    when(ctx.getCookies()).thenReturn(
        token == null ? Map.of() : Map.of(AUTH_TOKEN_KEY, new Cookie(AUTH_TOKEN_KEY, token)));
  }

  @Nested
  class TokenExtractionTests {

    @Test
    void aborts401WhenNoCookie() {
      when(ctx.getCookies()).thenReturn(Map.of());

      filter.filter(ctx);

      ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
      verify(ctx).abortWith(captor.capture());
      assertThat(captor.getValue().getStatus()).isEqualTo(401);
    }

    @Test
    void aborts401WhenBlankCookie() {
      setCookie("   ");

      filter.filter(ctx);

      ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
      verify(ctx).abortWith(captor.capture());
      assertThat(captor.getValue().getStatus()).isEqualTo(401);
    }

    @Test
    void setsTokenPropertyWhenCookiePresent() {
      setCookie("valid-token");

      filter.filter(ctx);

      verify(ctx).setProperty(AUTH_TOKEN_KEY, "valid-token");
      verify(ctx, never()).abortWith(org.mockito.ArgumentMatchers.any());
    }
  }

  @Nested
  class TokenValidationFilterTests {

    private UserService userService;
    private TokenAuthFilter.TokenValidationFilter validationFilter;

    @BeforeEach
    void setUp() {
      userService = mock(UserService.class);
      validationFilter = new TokenAuthFilter.TokenValidationFilter(userService);
    }

    @Test
    void passesWhenTokenIsValid() {
      when(ctx.getProperty(AUTH_TOKEN_KEY)).thenReturn("valid-token");
      when(userService.getUserMyself("valid-token")).thenReturn(Optional.of(
          new UserMyself("user-1", "u@x.com", "U", "x.com", "ACTIVE", "INTERNAL", "en", Map.of())));

      validationFilter.filter(ctx);

      verify(ctx, never()).abortWith(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void aborts401WhenTokenIsInvalid() {
      when(ctx.getProperty(AUTH_TOKEN_KEY)).thenReturn("bad-token");
      when(userService.getUserMyself("bad-token")).thenReturn(Optional.empty());

      validationFilter.filter(ctx);

      ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
      verify(ctx).abortWith(captor.capture());
      assertThat(captor.getValue().getStatus()).isEqualTo(401);
    }

    @Test
    void aborts401WhenTokenPropertyMissing() {
      when(ctx.getProperty(AUTH_TOKEN_KEY)).thenReturn(null);

      validationFilter.filter(ctx);

      ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
      verify(ctx).abortWith(captor.capture());
      assertThat(captor.getValue().getStatus()).isEqualTo(401);
    }
  }
}
