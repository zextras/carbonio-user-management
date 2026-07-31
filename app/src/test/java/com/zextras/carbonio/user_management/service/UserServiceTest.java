// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Ticker;
import com.zextras.carbonio.quarkus.extensions.bootstrap.ApplicationConfigService;
import com.zextras.carbonio.user_management.cache.UserInfoCache;
import com.zextras.carbonio.user_management.cache.UserMyselfCache;
import com.zextras.carbonio.user_management.record.UserInfo;
import com.zextras.carbonio.user_management.record.UserMyself;
import com.zextras.mailbox.client.MailboxClientException;
import com.zextras.mailbox.client.MailboxServerException;
import com.zextras.mailbox.client.internal.AccountInfo;
import com.zextras.mailbox.client.internal.AccountStatus;
import com.zextras.mailbox.client.internal.MailboxInternalApiClient;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UserServiceTest {

  private MailboxInternalApiClient internalClient;
  private UserInfoCache userInfoCache;
  private UserMyselfCache userMyselfCache;
  private UserService userService;

  @BeforeEach
  void setUp() {
    internalClient = mock(MailboxInternalApiClient.class);
    userInfoCache = mock(UserInfoCache.class);
    userMyselfCache = mock(UserMyselfCache.class);
    when(userInfoCache.isCacheEnabled()).thenReturn(true);
    when(userMyselfCache.isCacheEnabled()).thenReturn(true);
    userService =
        new UserService(
            internalClient,
            userInfoCache,
            userMyselfCache,
            org.eclipse.microprofile.context.ManagedExecutor.builder().build());
  }

  private UserInfo sampleUserInfo() {
    return new UserInfo(
        "user-1", "user@example.com", "John Doe", "example.com", "ACTIVE", "INTERNAL");
  }

  private UserMyself sampleMyself() {
    return new UserMyself(
        "user-1",
        "user@example.com",
        "John Doe",
        "example.com",
        "ACTIVE",
        "INTERNAL",
        "en",
        List.of("carbonioFeatureFilesEnabled"),
        Map.of());
  }

  private long futureExpiresAt() {
    return System.currentTimeMillis() + 3_600_000L;
  }

  private AccountInfo sampleAccountInfo(String userId, String email) {
    return new AccountInfo(
        userId,
        email,
        "Test User",
        "cos-1",
        "dom-1",
        "example.com",
        AccountStatus.active,
        false,
        false,
        false,
        "en",
        Map.of("carbonioFeatureFilesEnabled", true),
        Map.of(),
        3_600_000L);
  }

  @Nested
  class GetUserMyselfTests {

    @Test
    void returnsCachedResultWhenCacheHit() {
      UserMyself myself = sampleMyself();
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.of(myself));
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.of(sampleUserInfo()));

      Optional<UserMyself> result = userService.getUserMyself("token-1", false);

      assertThat(result).contains(myself);
      verifyNoInteractions(internalClient);
    }

    @Test
    void cacheMiss_callsInternalApi() throws Exception {
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.empty());
      when(internalClient.getMyAccountInfo("token-1"))
          .thenThrow(new MailboxClientException("test"));

      Optional<UserMyself> result = userService.getUserMyself("token-1", false);

      assertThat(result).isEmpty();
      verify(internalClient).getMyAccountInfo("token-1");
    }

    @Test
    void cacheHit_warmsUserInfoIfAbsent() {
      UserMyself myself = sampleMyself();
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.of(myself));
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());

      userService.getUserMyself("token-1", false);

      verify(userInfoCache).put(any(UserInfo.class));
    }

    @Test
    void cacheHit_skipsWarmingWhenUserInfoAlreadyCached() {
      UserMyself myself = sampleMyself();
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.of(myself));
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.of(sampleUserInfo()));

      userService.getUserMyself("token-1", false);

      verify(userInfoCache, never()).put(any());
    }

    @Test
    void apiSuccess_cachesResultAndReturnsMyself() throws Exception {
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.empty());

      AccountInfo accountInfo =
          new AccountInfo(
              "user-1",
              "user@example.com",
              "John Doe",
              "cos-1",
              "dom-1",
              "example.com",
              AccountStatus.active,
              false,
              false,
              false,
              "en",
              Map.of("carbonioFeatureFilesEnabled", true),
              Map.of(),
              3_600_000L);
      when(internalClient.getMyAccountInfo("token-1")).thenReturn(accountInfo);
      when(userMyselfCache.computeExpiresAt(anyLong())).thenReturn(futureExpiresAt());
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());

      Optional<UserMyself> result = userService.getUserMyself("token-1", false);

      assertThat(result).isPresent();
      assertThat(result.get().userId()).isEqualTo("user-1");
      verify(userMyselfCache).put(eq("token-1"), eq("user-1"), any(), anyLong());
    }

    @Test
    void apiFailure_returnsEmpty() throws Exception {
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.empty());
      when(internalClient.getMyAccountInfo("token-1"))
          .thenThrow(new MailboxClientException("test"));

      Optional<UserMyself> result = userService.getUserMyself("token-1", false);

      assertThat(result).isEmpty();
      verify(userMyselfCache, never()).put(any(), any(), any(), anyLong());
    }

    @Test
    void cacheDisabled_apiResultNotCached() throws Exception {
      when(userMyselfCache.isCacheEnabled()).thenReturn(false);
      when(userInfoCache.isCacheEnabled()).thenReturn(false);
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.empty());
      when(internalClient.getMyAccountInfo("token-1"))
          .thenThrow(new MailboxClientException("test"));

      Optional<UserMyself> result = userService.getUserMyself("token-1", false);

      assertThat(result).isEmpty();
      verify(userMyselfCache, never()).put(any(), any(), any(), anyLong());
    }

    @Test
    void bypassCache_ignoresCachedEntryAndRevalidatesAgainstMailbox() throws Exception {
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.of(sampleMyself()));
      when(internalClient.getMyAccountInfo("token-1"))
          .thenReturn(sampleAccountInfo("user-1", "user@example.com"));
      when(userMyselfCache.computeExpiresAt(anyLong())).thenReturn(futureExpiresAt());
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.of(sampleUserInfo()));

      Optional<UserMyself> result = userService.getUserMyself("token-1", true);

      assertThat(result).isPresent();
      assertThat(result.get().userId()).isEqualTo("user-1");
      verify(userMyselfCache, never()).getByToken("token-1");
      verify(internalClient).getMyAccountInfo("token-1");
    }

    @Test
    void bypassCache_stillWritesFreshResultBackToCache() throws Exception {
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.of(sampleMyself()));
      when(internalClient.getMyAccountInfo("token-1"))
          .thenReturn(sampleAccountInfo("user-1", "user@example.com"));
      when(userMyselfCache.computeExpiresAt(anyLong())).thenReturn(futureExpiresAt());
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.of(sampleUserInfo()));

      userService.getUserMyself("token-1", true);

      verify(userMyselfCache).put(eq("token-1"), eq("user-1"), any(), anyLong());
    }

    @Test
    void bypassCache_returnsEmptyWhenMailboxRejectsTheToken() throws Exception {
      // A revoked session must stop working immediately for a bypassing caller, even though a
      // stale entry is still cached.
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.of(sampleMyself()));
      when(internalClient.getMyAccountInfo("token-1"))
          .thenThrow(new MailboxClientException("test"));

      Optional<UserMyself> result = userService.getUserMyself("token-1", true);

      assertThat(result).isEmpty();
    }
  }

  /**
   * Same scenario as above but against a real {@link UserMyselfCache}, so the hit/miss accounting
   * is the production one and the mailbox call count can be asserted end to end.
   */
  @Nested
  class GetUserMyselfBypassWithRealCacheTests {

    private UserService serviceWithRealCache() {
      ApplicationConfigService configService = mock(ApplicationConfigService.class);
      when(configService.get("cache.usermyself-ttl")).thenReturn(Optional.empty());
      UserMyselfCache realCache =
          new UserMyselfCache(configService, Ticker.systemTicker(), Clock.systemUTC());
      return new UserService(
          internalClient,
          userInfoCache,
          realCache,
          org.eclipse.microprofile.context.ManagedExecutor.builder().build());
    }

    @Test
    void secondCallHitsTheCache_thirdCallBypassesAndHitsMailboxAgain() throws Exception {
      UserService service = serviceWithRealCache();
      when(internalClient.getMyAccountInfo("token-1"))
          .thenReturn(sampleAccountInfo("user-1", "user@example.com"));
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.of(sampleUserInfo()));

      service.getUserMyself("token-1", false); // cold: mailbox hit #1
      service.getUserMyself("token-1", false); // warm: served from cache
      verify(internalClient, times(1)).getMyAccountInfo("token-1");

      service.getUserMyself("token-1", true); // bypass: mailbox hit #2
      verify(internalClient, times(2)).getMyAccountInfo("token-1");
    }

    @Test
    void bypassRefreshesTheEntryInsteadOfDisablingTheCache() throws Exception {
      UserService service = serviceWithRealCache();
      when(internalClient.getMyAccountInfo("token-1"))
          .thenReturn(sampleAccountInfo("user-1", "user@example.com"));
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.of(sampleUserInfo()));

      service.getUserMyself("token-1", true); // bypass on a cold cache: mailbox hit #1
      service.getUserMyself("token-1", false); // must now be served from cache

      verify(internalClient, times(1)).getMyAccountInfo("token-1");
    }
  }

  @Nested
  class GetUserByIdTests {

    @Test
    void returnsCachedResultWhenCacheHit() {
      UserInfo userInfo = sampleUserInfo();
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.of(userInfo));

      Optional<UserInfo> result = userService.getUserById("user-1");

      assertThat(result).contains(userInfo);
      verifyNoInteractions(internalClient);
    }

    @Test
    void cacheMiss_callsInternalApi() throws Exception {
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
      when(internalClient.getAccountInfo("user-1")).thenThrow(new MailboxClientException("test"));

      Optional<UserInfo> result = userService.getUserById("user-1");

      assertThat(result).isEmpty();
      verify(internalClient).getAccountInfo("user-1");
    }

    @Test
    void apiSuccess_cachesAndReturnsUserInfo() throws Exception {
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
      when(internalClient.getAccountInfo("user-1"))
          .thenReturn(sampleAccountInfo("user-1", "user@example.com"));

      Optional<UserInfo> result = userService.getUserById("user-1");

      assertThat(result).isPresent();
      assertThat(result.get().userId()).isEqualTo("user-1");
      verify(userInfoCache).put(any(UserInfo.class));
    }

    @Test
    void apiFailure_returnsEmpty() throws Exception {
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
      when(internalClient.getAccountInfo("user-1")).thenThrow(new MailboxClientException("test"));

      Optional<UserInfo> result = userService.getUserById("user-1");

      assertThat(result).isEmpty();
      verify(userInfoCache, never()).put(any());
    }

    @Test
    void cacheDisabled_apiResultNotCached() throws Exception {
      when(userInfoCache.isCacheEnabled()).thenReturn(false);
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
      when(internalClient.getAccountInfo("user-1")).thenThrow(new MailboxClientException("test"));

      Optional<UserInfo> result = userService.getUserById("user-1");

      assertThat(result).isEmpty();
      verify(userInfoCache, never()).put(any());
    }
  }

  @Nested
  class GetUserByEmailTests {

    @Test
    void returnsCachedResultWhenCacheHit() {
      UserInfo userInfo = sampleUserInfo();
      when(userInfoCache.getByEmail("user@example.com")).thenReturn(Optional.of(userInfo));

      Optional<UserInfo> result = userService.getUserByEmail("user@example.com");

      assertThat(result).contains(userInfo);
      verifyNoInteractions(internalClient);
    }

    @Test
    void cacheMiss_callsInternalApi() throws Exception {
      when(userInfoCache.getByEmail("user@example.com")).thenReturn(Optional.empty());
      when(internalClient.getAccountByEmail("user@example.com"))
          .thenThrow(new MailboxClientException("test"));

      Optional<UserInfo> result = userService.getUserByEmail("user@example.com");

      assertThat(result).isEmpty();
      verify(internalClient).getAccountByEmail("user@example.com");
    }

    @Test
    void apiSuccess_cachesAndReturnsUserInfo() throws Exception {
      when(userInfoCache.getByEmail("user@example.com")).thenReturn(Optional.empty());
      when(internalClient.getAccountByEmail("user@example.com"))
          .thenReturn(sampleAccountInfo("user-1", "user@example.com"));

      Optional<UserInfo> result = userService.getUserByEmail("user@example.com");

      assertThat(result).isPresent();
      assertThat(result.get().email()).isEqualTo("user@example.com");
      verify(userInfoCache).put(any(UserInfo.class));
    }

    @Test
    void apiFailure_returnsEmpty() throws Exception {
      when(userInfoCache.getByEmail("user@example.com")).thenReturn(Optional.empty());
      when(internalClient.getAccountByEmail("user@example.com"))
          .thenThrow(new MailboxClientException("test"));

      Optional<UserInfo> result = userService.getUserByEmail("user@example.com");

      assertThat(result).isEmpty();
      verify(userInfoCache, never()).put(any());
    }
  }

  @Nested
  class GetUsersTests {

    @Test
    void allCacheHitsSkipsApi() {
      UserInfo u1 = sampleUserInfo();
      UserInfo u2 =
          new UserInfo("user-2", "u2@example.com", "Jane", "example.com", "ACTIVE", "INTERNAL");
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.of(u1));
      when(userInfoCache.getByUserId("user-2")).thenReturn(Optional.of(u2));

      List<UserInfo> result = userService.getUsers(List.of("user-1", "user-2"));

      assertThat(result).containsExactly(u1, u2);
      verifyNoInteractions(internalClient);
    }

    @Test
    void cacheMiss_usesBatchEndpoint() throws Exception {
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
      when(internalClient.batchGetAccountsByIds(List.of("user-1")))
          .thenReturn(List.of(sampleAccountInfo("user-1", "user@example.com")));

      List<UserInfo> result = userService.getUsers(List.of("user-1"));

      assertThat(result).hasSize(1);
      assertThat(result.get(0).userId()).isEqualTo("user-1");
    }

    @Test
    void deduplicatesUserIds() throws Exception {
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
      when(internalClient.batchGetAccountsByIds(List.of("user-1")))
          .thenReturn(List.of(sampleAccountInfo("user-1", "user@example.com")));

      List<UserInfo> result = userService.getUsers(List.of("user-1", "user-1"));

      assertThat(result).hasSize(1);
    }

    @Test
    void batchFails_fallsBackToIndividualLookups() throws Exception {
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
      when(internalClient.batchGetAccountsByIds(any()))
          .thenThrow(new MailboxServerException("batch error"));
      when(internalClient.getAccountInfo("user-1"))
          .thenReturn(sampleAccountInfo("user-1", "user@example.com"));

      List<UserInfo> result = userService.getUsers(List.of("user-1"));

      assertThat(result).hasSize(1);
      assertThat(result.get(0).userId()).isEqualTo("user-1");
      verify(internalClient).getAccountInfo("user-1");
    }

    @Test
    void returnsOnlyFoundUsers() throws Exception {
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.of(sampleUserInfo()));
      when(userInfoCache.getByUserId("user-not-found")).thenReturn(Optional.empty());
      when(internalClient.batchGetAccountsByIds(List.of("user-not-found")))
          .thenThrow(new MailboxClientException("not found"));
      when(internalClient.getAccountInfo("user-not-found"))
          .thenThrow(new MailboxClientException("not found"));

      List<UserInfo> result = userService.getUsers(List.of("user-1", "user-not-found"));

      assertThat(result).hasSize(1);
      assertThat(result.get(0).userId()).isEqualTo("user-1");
    }

    @Test
    void emptyInputReturnsEmpty() {
      List<UserInfo> result = userService.getUsers(List.of());
      assertThat(result).isEmpty();
      verifyNoInteractions(internalClient);
    }
  }
}
