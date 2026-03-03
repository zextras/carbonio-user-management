// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.rest;

import static com.zextras.carbonio.user_management.UserManagementServiceConfig.AUTH_TOKEN_KEY;
import static com.zextras.carbonio.user_management.UserManagementServiceConfig.FeatureFlags;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zextras.carbonio.user_management.record.UserInfo;
import com.zextras.carbonio.user_management.record.UserMyself;
import com.zextras.carbonio.user_management.rest.dto.MyselfDto;
import com.zextras.carbonio.user_management.rest.dto.UserInfoDto;
import com.zextras.carbonio.user_management.service.UserService;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UserResourceTest {

  private UserService userService;
  private UserResource resource;
  private ContainerRequestContext ctx;

  @BeforeEach
  void setUp() {
    userService = mock(UserService.class);
    resource = new UserResource(userService);
    ctx = mock(ContainerRequestContext.class);
    when(ctx.getProperty(AUTH_TOKEN_KEY)).thenReturn("token-1");
  }

  private UserInfo sampleUserInfo() {
    return new UserInfo("user-1", "user@example.com", "John Doe",
        "example.com", "ACTIVE", "INTERNAL");
  }

  @Nested
  class GetMyselfTests {

    @Test
    void returnsOkWithMyselfDto() {
      UserMyself myself = new UserMyself(
          "user-1", "user@example.com", "John Doe", "example.com",
          "ACTIVE", "INTERNAL", "it", Map.of(FeatureFlags.FILES_ENABLED, true));
      when(userService.getUserMyself("token-1")).thenReturn(Optional.of(myself));

      Response response = resource.getMyself(ctx);

      assertThat(response.getStatus()).isEqualTo(200);
      MyselfDto dto = (MyselfDto) response.getEntity();
      assertThat(dto.info().userId()).isEqualTo("user-1");
      assertThat(dto.locale()).isEqualTo("it");
      assertThat(dto.featureList()).containsEntry(FeatureFlags.FILES_ENABLED, true);
    }

    @Test
    void returns401WhenTokenInvalid() {
      when(userService.getUserMyself("token-1")).thenReturn(Optional.empty());

      Response response = resource.getMyself(ctx);

      assertThat(response.getStatus()).isEqualTo(401);
    }
  }

  @Nested
  class GetByIdTests {

    @Test
    void returnsOkWithUserInfoDto() {
      when(userService.getUserById("user-1", "token-1"))
          .thenReturn(Optional.of(sampleUserInfo()));

      Response response = resource.getById("user-1", ctx);

      assertThat(response.getStatus()).isEqualTo(200);
      UserInfoDto dto = (UserInfoDto) response.getEntity();
      assertThat(dto.userId()).isEqualTo("user-1");
      assertThat(dto.email()).isEqualTo("user@example.com");
    }

    @Test
    void returns404WhenUserNotFound() {
      when(userService.getUserById("missing", "token-1")).thenReturn(Optional.empty());

      Response response = resource.getById("missing", ctx);

      assertThat(response.getStatus()).isEqualTo(404);
    }
  }

  @Nested
  class GetByEmailTests {

    @Test
    void returnsOkWithUserInfoDto() {
      when(userService.getUserByEmail("user@example.com", "token-1"))
          .thenReturn(Optional.of(sampleUserInfo()));

      Response response = resource.getByEmail("user@example.com", ctx);

      assertThat(response.getStatus()).isEqualTo(200);
      UserInfoDto dto = (UserInfoDto) response.getEntity();
      assertThat(dto.fullName()).isEqualTo("John Doe");
    }

    @Test
    void returns404WhenUserNotFound() {
      when(userService.getUserByEmail("nope@x.com", "token-1")).thenReturn(Optional.empty());

      Response response = resource.getByEmail("nope@x.com", ctx);

      assertThat(response.getStatus()).isEqualTo(404);
    }
  }

  @Nested
  class GetUsersTests {

    @Test
    void returnsOkWithUserList() {
      UserInfo u1 = new UserInfo("id-1", "a@x.com", "A", "x.com", "ACTIVE", "INTERNAL");
      UserInfo u2 = new UserInfo("id-2", "b@x.com", "B", "x.com", "CLOSED", "GUEST");
      when(userService.getUsers(anyList(), anyString())).thenReturn(List.of(u1, u2));

      Response response = resource.getUsers(List.of("id-1", "id-2"), ctx);

      assertThat(response.getStatus()).isEqualTo(200);
      @SuppressWarnings("unchecked")
      List<UserInfoDto> dtos = (List<UserInfoDto>) response.getEntity();
      assertThat(dtos).hasSize(2);
      assertThat(dtos.get(0).userId()).isEqualTo("id-1");
      assertThat(dtos.get(1).status()).isEqualTo("CLOSED");
    }

    @Test
    void returnsEmptyList() {
      when(userService.getUsers(anyList(), anyString())).thenReturn(List.of());

      Response response = resource.getUsers(List.of("bad-id"), ctx);

      assertThat(response.getStatus()).isEqualTo(200);
      @SuppressWarnings("unchecked")
      List<UserInfoDto> dtos = (List<UserInfoDto>) response.getEntity();
      assertThat(dtos).isEmpty();
    }
  }
}
