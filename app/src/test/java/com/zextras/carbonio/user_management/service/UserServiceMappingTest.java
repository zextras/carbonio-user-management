// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.zextras.carbonio.user_management.cache.UserInfoCache;
import com.zextras.carbonio.user_management.cache.UserMyselfCache;
import com.zextras.carbonio.user_management.record.UserInfo;
import com.zextras.carbonio.user_management.record.UserMyself;
import com.zextras.mailbox.client.internal.AccountInfo;
import com.zextras.mailbox.client.internal.AccountStatus;
import com.zextras.mailbox.client.internal.MailboxInternalApiClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for AccountInfo mapping methods in UserService.
 * In the same package to access package-private methods.
 */
class UserServiceMappingTest {

  private UserService userService;

  @BeforeEach
  void setUp() {
    userService = new UserService(
        mock(MailboxInternalApiClient.class),
        mock(UserInfoCache.class),
        mock(UserMyselfCache.class),
        org.eclipse.microprofile.context.ManagedExecutor.builder().build());
  }

  private AccountInfo accountInfo(String id, String name, String displayName, String domain,
      AccountStatus status, boolean isExternal) {
    return new AccountInfo(id, name, displayName, "cos-1", "dom-1",
        domain, status, false, isExternal, "en", Map.of(), Map.of(), 3_600_000L);
  }

  @Nested
  class MapAccountInfoToUserInfoTests {

    @Test
    void mapsAllBasicFields() {
      AccountInfo info = accountInfo("uid-123", "user@example.com", "Jane Doe",
          "example.com", AccountStatus.active, false);

      UserInfo result = userService.mapAccountInfoToUserInfo(info);

      assertThat(result.userId()).isEqualTo("uid-123");
      assertThat(result.email()).isEqualTo("user@example.com");
      assertThat(result.fullName()).isEqualTo("Jane Doe");
      assertThat(result.domain()).isEqualTo("example.com");
      assertThat(result.status()).isEqualTo("ACTIVE");
      assertThat(result.type()).isEqualTo("INTERNAL");
    }

    @Test
    void mapsGuestUser() {
      AccountInfo info = accountInfo("uid-guest", "guest@example.com", "Guest User",
          "example.com", AccountStatus.active, true);

      UserInfo result = userService.mapAccountInfoToUserInfo(info);

      assertThat(result.type()).isEqualTo("GUEST");
    }

    @Test
    void mapsInternalWhenNotExternal() {
      AccountInfo info = accountInfo("uid-1", "user@example.com", "User",
          "example.com", AccountStatus.active, false);

      UserInfo result = userService.mapAccountInfoToUserInfo(info);

      assertThat(result.type()).isEqualTo("INTERNAL");
    }

    @Test
    void mapsAccountStatusUppercased() {
      AccountInfo info = accountInfo("uid-1", "user@example.com", "User",
          "example.com", AccountStatus.locked, false);

      UserInfo result = userService.mapAccountInfoToUserInfo(info);

      assertThat(result.status()).isEqualTo("LOCKED");
    }

    @Test
    void mapsClosedStatus() {
      AccountInfo info = accountInfo("uid-1", "user@example.com", "User",
          "example.com", AccountStatus.closed, false);

      UserInfo result = userService.mapAccountInfoToUserInfo(info);

      assertThat(result.status()).isEqualTo("CLOSED");
    }

    @Test
    void nullDisplayNameBecomesEmptyString() {
      AccountInfo info = new AccountInfo("uid-1", "user@example.com", null, "cos-1", "dom-1",
          "example.com", AccountStatus.active, false, false, "en", Map.of(), Map.of(), null);

      UserInfo result = userService.mapAccountInfoToUserInfo(info);

      assertThat(result.fullName()).isEmpty();
    }

    @Test
    void nullDomainBecomesEmptyString() {
      AccountInfo info = new AccountInfo("uid-1", "user@example.com", "User", "cos-1", "dom-1",
          null, AccountStatus.active, false, false, "en", Map.of(), Map.of(), null);

      UserInfo result = userService.mapAccountInfoToUserInfo(info);

      assertThat(result.domain()).isEmpty();
    }

    @Test
    void nullStatusDefaultsToActive() {
      AccountInfo info = new AccountInfo("uid-1", "user@example.com", "User", "cos-1", "dom-1",
          "example.com", null, false, false, "en", Map.of(), Map.of(), null);

      UserInfo result = userService.mapAccountInfoToUserInfo(info);

      assertThat(result.status()).isEqualTo("ACTIVE");
    }
  }

  @Nested
  class MapAccountInfoToUserMyselfTests {

    @Test
    void mapsAllBasicFields() {
      AccountInfo info = new AccountInfo(
          "uid-1", "user@example.com", "John Doe", "cos-1", "dom-1",
          "example.com", AccountStatus.active, false, false, "it",
          Map.of("carbonioFeatureFilesEnabled", true, "carbonioFeatureWscEnabled", false),
          Map.of("carbonioWscMaxGroupMembers", "50"),
          3_600_000L);

      UserMyself result = userService.mapAccountInfoToUserMyself(info);

      assertThat(result.userId()).isEqualTo("uid-1");
      assertThat(result.email()).isEqualTo("user@example.com");
      assertThat(result.fullName()).isEqualTo("John Doe");
      assertThat(result.domain()).isEqualTo("example.com");
      assertThat(result.status()).isEqualTo("ACTIVE");
      assertThat(result.type()).isEqualTo("INTERNAL");
      assertThat(result.locale()).isEqualTo("it");
    }

    @Test
    void mapsOnlyEnabledFeatures() {
      AccountInfo info = new AccountInfo(
          "uid-1", "user@example.com", "User", "cos-1", "dom-1",
          "example.com", AccountStatus.active, false, false, "en",
          Map.of("carbonioFeatureFilesEnabled", true, "carbonioFeatureWscEnabled", false),
          Map.of(), null);

      UserMyself result = userService.mapAccountInfoToUserMyself(info);

      assertThat(result.features()).containsExactly("carbonioFeatureFilesEnabled");
    }

    @Test
    void nullFeaturesBecomesEmptyList() {
      AccountInfo info = new AccountInfo(
          "uid-1", "user@example.com", "User", "cos-1", "dom-1",
          "example.com", AccountStatus.active, false, false, "en",
          null, Map.of(), null);

      UserMyself result = userService.mapAccountInfoToUserMyself(info);

      assertThat(result.features()).isEmpty();
    }

    @Test
    void nullCapabilitiesBecomesEmptyMap() {
      AccountInfo info = new AccountInfo(
          "uid-1", "user@example.com", "User", "cos-1", "dom-1",
          "example.com", AccountStatus.active, false, false, "en",
          Map.of(), null, null);

      UserMyself result = userService.mapAccountInfoToUserMyself(info);

      assertThat(result.capabilities()).isEmpty();
    }

    @Test
    void mapsCapabilities() {
      AccountInfo info = new AccountInfo(
          "uid-1", "user@example.com", "User", "cos-1", "dom-1",
          "example.com", AccountStatus.active, false, false, "en",
          Map.of(),
          Map.of("carbonioWscMaxGroupMembers", "50", "carbonioFilesMaxUploadSize", "1048576"),
          null);

      UserMyself result = userService.mapAccountInfoToUserMyself(info);

      assertThat(result.capabilities())
          .containsEntry("carbonioWscMaxGroupMembers", "50")
          .containsEntry("carbonioFilesMaxUploadSize", "1048576");
    }

    @Test
    void nullLocaleDefaultsToEnglish() {
      AccountInfo info = new AccountInfo(
          "uid-1", "user@example.com", "User", "cos-1", "dom-1",
          "example.com", AccountStatus.active, false, false, null,
          Map.of(), Map.of(), null);

      UserMyself result = userService.mapAccountInfoToUserMyself(info);

      assertThat(result.locale()).isEqualTo("en");
    }

    @Test
    void mapsGuestType() {
      AccountInfo info = new AccountInfo(
          "uid-1", "guest@example.com", "Guest", "cos-1", "dom-1",
          "example.com", AccountStatus.active, false, true, "en",
          Map.of(), Map.of(), null);

      UserMyself result = userService.mapAccountInfoToUserMyself(info);

      assertThat(result.type()).isEqualTo("GUEST");
    }

    @Test
    void nullStatusDefaultsToActive() {
      AccountInfo info = new AccountInfo(
          "uid-1", "user@example.com", "User", "cos-1", "dom-1",
          "example.com", null, false, false, "en",
          Map.of(), Map.of(), null);

      UserMyself result = userService.mapAccountInfoToUserMyself(info);

      assertThat(result.status()).isEqualTo("ACTIVE");
    }
  }
}
