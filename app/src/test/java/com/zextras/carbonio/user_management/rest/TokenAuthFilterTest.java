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
import jakarta.ws.rs.core.UriInfo;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TokenAuthFilterTest {

  private UserService userService;
  private TokenAuthFilter filter;
  private ContainerRequestContext ctx;
  private UriInfo uriInfo;

  @BeforeEach
  void setUp() {
    userService = mock(UserService.class);
    filter = new TokenAuthFilter(userService);
    ctx = mock(ContainerRequestContext.class);
    uriInfo = mock(UriInfo.class);
    when(ctx.getUriInfo()).thenReturn(uriInfo);
  }

  private void setCookie(String token) {
    when(ctx.getCookies()).thenReturn(
        token == null ? Map.of() : Map.of(AUTH_TOKEN_KEY, new Cookie(AUTH_TOKEN_KEY, token)));
  }

  private void setPath(String path) {
    when(uriInfo.getPath()).thenReturn(path);
  }

  @Nested
  class NoCookieTests {

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
      setPath("/users");

      filter.filter(ctx);

      ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
      verify(ctx).abortWith(captor.capture());
      assertThat(captor.getValue().getStatus()).isEqualTo(401);
    }
  }

  @Nested
  class MyselfPathTests {

    @Test
    void passesWithoutPreAuthForMyselfPath() {
      setCookie("valid-token");
      setPath("/users/myself");

      filter.filter(ctx);

      verify(ctx).setProperty(AUTH_TOKEN_KEY, "valid-token");
      verify(ctx, never()).abortWith(org.mockito.ArgumentMatchers.any());
      verify(userService, never()).getUserMyself(org.mockito.ArgumentMatchers.any());
    }
  }

  @Nested
  class OtherEndpointTests {

    @Test
    void passesWhenTokenIsValid() {
      setCookie("valid-token");
      setPath("/users/id/user-1");
      when(userService.getUserMyself("valid-token")).thenReturn(Optional.of(
          new UserMyself("user-1", "u@x.com", "U", "x.com", "ACTIVE", "INTERNAL", "en", Map.of())));

      filter.filter(ctx);

      verify(ctx).setProperty(AUTH_TOKEN_KEY, "valid-token");
      verify(ctx, never()).abortWith(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void aborts401WhenTokenIsInvalid() {
      setCookie("bad-token");
      setPath("/users/id/user-1");
      when(userService.getUserMyself("bad-token")).thenReturn(Optional.empty());

      filter.filter(ctx);

      ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
      verify(ctx).abortWith(captor.capture());
      assertThat(captor.getValue().getStatus()).isEqualTo(401);
    }
  }
}
