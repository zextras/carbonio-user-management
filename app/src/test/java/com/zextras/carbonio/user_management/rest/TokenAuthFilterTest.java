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

import com.zextras.carbonio.user_management.rest.TokenAuthFilter;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.Response;
import java.util.Map;
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
        token == null ? Map.of() : Map.of(AUTH_TOKEN_KEY,
            new Cookie.Builder(AUTH_TOKEN_KEY).value(token).build()));
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
}
