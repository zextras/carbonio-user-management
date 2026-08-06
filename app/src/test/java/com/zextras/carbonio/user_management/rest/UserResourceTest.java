// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.rest;

import static com.zextras.carbonio.user_management.UserManagementServiceConfig.MAX_BATCH_USER_IDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.zextras.carbonio.user_management.record.UserInfo;
import com.zextras.carbonio.user_management.record.UserMyself;
import com.zextras.carbonio.user_management.rest.dto.MyselfDto;
import com.zextras.carbonio.user_management.rest.dto.UserInfoDto;
import com.zextras.carbonio.user_management.service.UserService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.jboss.resteasy.reactive.RestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UserResourceTest {

  private UserService userService;
  private UserResource resource;

  @BeforeEach
  void setUp() {
    userService = mock(UserService.class);
    resource = new UserResource(userService);
  }

  private UserInfo sampleUserInfo() {
    return new UserInfo(
        "user-1", "user@example.com", "John Doe", "example.com", "ACTIVE", "INTERNAL");
  }

  @Nested
  class GetMyselfTests {

    @Test
    void returnsOkWithMyselfDto() {
      UserMyself myself =
          new UserMyself(
              "user-1",
              "user@example.com",
              "John Doe",
              "example.com",
              "ACTIVE",
              "INTERNAL",
              "it",
              List.of("carbonioFeatureFilesEnabled"),
              Map.of("carbonioWscMaxGroupMembers", "50"));
      when(userService.getUserMyself("token-1", false)).thenReturn(Optional.of(myself));

      RestResponse<MyselfDto> response = resource.getMyself("token-1", false);

      assertThat(response.getStatus()).isEqualTo(200);
      MyselfDto dto = response.getEntity();
      assertThat(dto.info().userId()).isEqualTo("user-1");
      assertThat(dto.locale()).isEqualTo("it");
      assertThat(dto.features()).containsExactly("carbonioFeatureFilesEnabled");
      assertThat(dto.capabilities()).containsEntry("carbonioWscMaxGroupMembers", "50");
    }

    @Test
    void returns401WhenTokenInvalid() {
      when(userService.getUserMyself("token-1", false)).thenReturn(Optional.empty());

      RestResponse<MyselfDto> response = resource.getMyself("token-1", false);

      assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void returns401WhenCookieTokenMissing() {
      RestResponse<MyselfDto> response = resource.getMyself(null, false);

      assertThat(response.getStatus()).isEqualTo(401);
      verifyNoInteractions(userService);
    }

    @Test
    void returns401WhenCookieTokenEmpty() {
      RestResponse<MyselfDto> response = resource.getMyself("", false);

      assertThat(response.getStatus()).isEqualTo(401);
      verifyNoInteractions(userService);
    }

    @Test
    void returns401WhenCookieTokenBlank() {
      RestResponse<MyselfDto> response = resource.getMyself("   ", false);

      assertThat(response.getStatus()).isEqualTo(401);
      verifyNoInteractions(userService);
    }
  }

  /**
   * {@code bypassCache} is the request-side opt out of the myself cache: the token is re-validated
   * against mailbox instead of being answered from the cached entry.
   */
  @Nested
  class GetMyselfBypassCacheTests {

    private UserMyself myself() {
      return new UserMyself(
          "user-1",
          "user@example.com",
          "John Doe",
          "example.com",
          "ACTIVE",
          "INTERNAL",
          "en",
          List.of(),
          Map.of());
    }

    @Test
    void forwardsBypassToTheService() {
      when(userService.getUserMyself("token-1", true)).thenReturn(Optional.of(myself()));

      RestResponse<MyselfDto> response = resource.getMyself("token-1", true);

      assertThat(response.getStatus()).isEqualTo(200);
      verify(userService).getUserMyself("token-1", true);
    }

    @Test
    void defaultsToNoBypassWhenParameterAbsent() {
      // JAX-RS binds a missing @QueryParam boolean to false
      when(userService.getUserMyself("token-1", false)).thenReturn(Optional.of(myself()));

      resource.getMyself("token-1", false);

      verify(userService).getUserMyself("token-1", false);
    }

    @Test
    void stillReturns401WhenTokenMissingEvenWithBypass() {
      RestResponse<MyselfDto> response = resource.getMyself(null, true);

      assertThat(response.getStatus()).isEqualTo(401);
      verifyNoInteractions(userService);
    }

    @Test
    void returns401WhenBypassRevalidationFails() {
      // A session revoked meanwhile: mailbox rejects the token, the stale cached entry must not
      // be used as a fallback.
      when(userService.getUserMyself("token-1", true)).thenReturn(Optional.empty());

      RestResponse<MyselfDto> response = resource.getMyself("token-1", true);

      assertThat(response.getStatus()).isEqualTo(401);
    }
  }

  @Nested
  class GetByIdTests {

    @Test
    void returnsOkWithUserInfoDto() {
      when(userService.getUserById("user-1")).thenReturn(Optional.of(sampleUserInfo()));

      RestResponse<UserInfoDto> response = resource.getById("user-1");

      assertThat(response.getStatus()).isEqualTo(200);
      UserInfoDto dto = response.getEntity();
      assertThat(dto.userId()).isEqualTo("user-1");
      assertThat(dto.email()).isEqualTo("user@example.com");
    }

    @Test
    void returns404WhenUserNotFound() {
      when(userService.getUserById("missing")).thenReturn(Optional.empty());

      RestResponse<UserInfoDto> response = resource.getById("missing");

      assertThat(response.getStatus()).isEqualTo(404);
    }
  }

  @Nested
  class GetByEmailTests {

    @Test
    void returnsOkWithUserInfoDto() {
      when(userService.getUserByEmail("user@example.com"))
          .thenReturn(Optional.of(sampleUserInfo()));

      RestResponse<UserInfoDto> response = resource.getByEmail("user@example.com");

      assertThat(response.getStatus()).isEqualTo(200);
      UserInfoDto dto = response.getEntity();
      assertThat(dto.fullName()).isEqualTo("John Doe");
    }

    @Test
    void returns404WhenUserNotFound() {
      when(userService.getUserByEmail("nope@x.com")).thenReturn(Optional.empty());

      RestResponse<UserInfoDto> response = resource.getByEmail("nope@x.com");

      assertThat(response.getStatus()).isEqualTo(404);
    }
  }

  @Nested
  class GetUsersTests {

    @Test
    void returnsOkWithUserList() {
      UserInfo u1 = new UserInfo("id-1", "a@x.com", "A", "x.com", "ACTIVE", "INTERNAL");
      UserInfo u2 = new UserInfo("id-2", "b@x.com", "B", "x.com", "CLOSED", "GUEST");
      when(userService.getUsers(anyList())).thenReturn(List.of(u1, u2));

      RestResponse<List<UserInfoDto>> response = resource.getUsers(List.of("id-1", "id-2"));

      assertThat(response.getStatus()).isEqualTo(200);
      List<UserInfoDto> dtos = response.getEntity();
      assertThat(dtos).hasSize(2);
      assertThat(dtos.get(0).userId()).isEqualTo("id-1");
      assertThat(dtos.get(1).status()).isEqualTo("CLOSED");
    }

    @Test
    void returnsEmptyList() {
      when(userService.getUsers(anyList())).thenReturn(List.of());

      RestResponse<List<UserInfoDto>> response = resource.getUsers(List.of("bad-id"));

      assertThat(response.getStatus()).isEqualTo(200);
      List<UserInfoDto> dtos = response.getEntity();
      assertThat(dtos).isEmpty();
    }

    @Test
    void returns400WhenUserIdsNull() {
      RestResponse<List<UserInfoDto>> response = resource.getUsers(null);

      assertThat(response.getStatus()).isEqualTo(400);
      verifyNoInteractions(userService);
    }

    @Test
    void returns400WhenExceedingMaxBatch() {
      List<String> ids =
          IntStream.rangeClosed(1, MAX_BATCH_USER_IDS + 1).mapToObj(i -> "id-" + i).toList();

      RestResponse<List<UserInfoDto>> response = resource.getUsers(ids);

      assertThat(response.getStatus()).isEqualTo(400);
      verifyNoInteractions(userService);
    }

    @Test
    void accepts200WhenExactlyAtMaxBatch() {
      List<String> ids =
          IntStream.rangeClosed(1, MAX_BATCH_USER_IDS).mapToObj(i -> "id-" + i).toList();
      when(userService.getUsers(anyList())).thenReturn(List.of());

      RestResponse<List<UserInfoDto>> response = resource.getUsers(ids);

      assertThat(response.getStatus()).isEqualTo(200);
    }
  }
}
