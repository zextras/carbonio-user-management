// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.zextras.carbonio.user_management.ldap.DirectoryException;
import com.zextras.carbonio.user_management.service.UserDirectory.AuthenticatedUser;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UserServiceTest {

  private UserDirectory directory;
  private UserInfoCache userInfoCache;
  private UserMyselfCache userMyselfCache;
  private UserService userService;

  @BeforeEach
  void setUp() {
    directory = mock(UserDirectory.class);
    userInfoCache = mock(UserInfoCache.class);
    userMyselfCache = mock(UserMyselfCache.class);
    when(userInfoCache.isCacheEnabled()).thenReturn(true);
    when(userMyselfCache.isCacheEnabled()).thenReturn(true);
    userService =
        new UserService(directory, userInfoCache, userMyselfCache);
  }

  private UserInfo sampleUserInfo() {
    return new UserInfo(
        "user-1",
        "user@example.com",
        "John Doe",
        "example.com",
        "ACTIVE",
        "INTERNAL",
        "cos-1",
        "dom-1",
        false);
  }

  private UserMyself sampleMyself() {
    return new UserMyself(
        "user-1",
        "user@example.com",
        "John Doe",
        "example.com",
        "ACTIVE",
        "INTERNAL",
        "cos-1",
        "dom-1",
        false,
        "en",
        List.of("carbonioFeatureFilesEnabled"),
        Map.of());
  }

  private long futureExpiresAt() {
    return System.currentTimeMillis() + 3_600_000L;
  }

  private AuthenticatedUser sampleAuthenticated() {
    return new AuthenticatedUser(sampleMyself(), 3_600_000L);
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
      verifyNoInteractions(directory);
    }

    @Test
    void cacheMiss_callsDirectory() throws Exception {
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.empty());
      when(directory.byToken("token-1")).thenReturn(Optional.empty());

      Optional<UserMyself> result = userService.getUserMyself("token-1", false);

      assertThat(result).isEmpty();
      verify(directory).byToken("token-1");
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
    void directorySuccess_cachesResultAndReturnsMyself() throws Exception {
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.empty());

      when(directory.byToken("token-1")).thenReturn(Optional.of(sampleAuthenticated()));
      when(userMyselfCache.computeExpiresAt(anyLong())).thenReturn(futureExpiresAt());
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());

      Optional<UserMyself> result = userService.getUserMyself("token-1", false);

      assertThat(result).isPresent();
      assertThat(result.get().userId()).isEqualTo("user-1");
      assertThat(result.get().cosId()).isEqualTo("cos-1");
      assertThat(result.get().domainId()).isEqualTo("dom-1");
      assertThat(result.get().isGlobalAdmin()).isFalse();
      verify(userMyselfCache).put(eq("token-1"), eq("user-1"), any(), anyLong());
    }

    @Test
    void directoryMiss_returnsEmpty() throws Exception {
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.empty());
      when(directory.byToken("token-1")).thenReturn(Optional.empty());

      Optional<UserMyself> result = userService.getUserMyself("token-1", false);

      assertThat(result).isEmpty();
      verify(userMyselfCache, never()).put(any(), any(), any(), anyLong());
    }

    @Test
    void cacheDisabled_directoryResultNotCached() throws Exception {
      when(userMyselfCache.isCacheEnabled()).thenReturn(false);
      when(userInfoCache.isCacheEnabled()).thenReturn(false);
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.empty());
      when(directory.byToken("token-1")).thenReturn(Optional.empty());

      Optional<UserMyself> result = userService.getUserMyself("token-1", false);

      assertThat(result).isEmpty();
      verify(userMyselfCache, never()).put(any(), any(), any(), anyLong());
    }

    @Test
    void bypassCache_ignoresCachedEntryAndRevalidatesAgainstDirectory() throws Exception {
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.of(sampleMyself()));
      when(directory.byToken("token-1")).thenReturn(Optional.of(sampleAuthenticated()));
      when(userMyselfCache.computeExpiresAt(anyLong())).thenReturn(futureExpiresAt());
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.of(sampleUserInfo()));

      Optional<UserMyself> result = userService.getUserMyself("token-1", true);

      assertThat(result).isPresent();
      assertThat(result.get().userId()).isEqualTo("user-1");
      verify(userMyselfCache, never()).getByToken("token-1");
      verify(directory).byToken("token-1");
    }

    @Test
    void bypassCache_stillWritesFreshResultBackToCache() throws Exception {
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.of(sampleMyself()));
      when(directory.byToken("token-1")).thenReturn(Optional.of(sampleAuthenticated()));
      when(userMyselfCache.computeExpiresAt(anyLong())).thenReturn(futureExpiresAt());
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.of(sampleUserInfo()));

      userService.getUserMyself("token-1", true);

      verify(userMyselfCache).put(eq("token-1"), eq("user-1"), any(), anyLong());
    }

    @Test
    void bypassCache_returnsEmptyWhenDirectoryRejectsTheToken() throws Exception {
      // A revoked session must stop working immediately for a bypassing caller, even though a
      // stale entry is still cached.
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.of(sampleMyself()));
      when(directory.byToken("token-1")).thenReturn(Optional.empty());

      Optional<UserMyself> result = userService.getUserMyself("token-1", true);

      assertThat(result).isEmpty();
    }
  }

  /**
   * Same scenario as above but against a real {@link UserMyselfCache}, so the hit/miss accounting
   * is the production one and the directory call count can be asserted end to end.
   */
  @Nested
  class GetUserMyselfBypassWithRealCacheTests {

    private UserService serviceWithRealCache() {
      ApplicationConfigService configService = mock(ApplicationConfigService.class);
      when(configService.get("cache.usermyself-ttl")).thenReturn(Optional.empty());
      UserMyselfCache realCache =
          new UserMyselfCache(configService, Ticker.systemTicker(), Clock.systemUTC());
      return new UserService(directory, userInfoCache, realCache);
    }

    @Test
    void secondCallHitsTheCache_thirdCallBypassesAndHitsDirectoryAgain() throws Exception {
      UserService service = serviceWithRealCache();
      when(directory.byToken("token-1")).thenReturn(Optional.of(sampleAuthenticated()));
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.of(sampleUserInfo()));

      service.getUserMyself("token-1", false); // cold: directory hit #1
      service.getUserMyself("token-1", false); // warm: served from cache
      verify(directory, times(1)).byToken("token-1");

      service.getUserMyself("token-1", true); // bypass: directory hit #2
      verify(directory, times(2)).byToken("token-1");
    }

    @Test
    void bypassRefreshesTheEntryInsteadOfDisablingTheCache() throws Exception {
      UserService service = serviceWithRealCache();
      when(directory.byToken("token-1")).thenReturn(Optional.of(sampleAuthenticated()));
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.of(sampleUserInfo()));

      service.getUserMyself("token-1", true); // bypass on a cold cache: directory hit #1
      service.getUserMyself("token-1", false); // must now be served from cache

      verify(directory, times(1)).byToken("token-1");
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
      verifyNoInteractions(directory);
    }

    @Test
    void cacheMiss_callsDirectory() throws Exception {
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
      when(directory.byId("user-1")).thenReturn(Optional.empty());

      Optional<UserInfo> result = userService.getUserById("user-1");

      assertThat(result).isEmpty();
      verify(directory).byId("user-1");
    }

    @Test
    void directorySuccess_cachesAndReturnsUserInfo() throws Exception {
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
      when(directory.byId("user-1")).thenReturn(Optional.of(sampleUserInfo()));

      Optional<UserInfo> result = userService.getUserById("user-1");

      assertThat(result).isPresent();
      assertThat(result.get().userId()).isEqualTo("user-1");
      assertThat(result.get().cosId()).isEqualTo("cos-1");
      assertThat(result.get().domainId()).isEqualTo("dom-1");
      verify(userInfoCache).put(any(UserInfo.class));
    }

    @Test
    void directoryMiss_returnsEmpty() throws Exception {
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
      when(directory.byId("user-1")).thenReturn(Optional.empty());

      Optional<UserInfo> result = userService.getUserById("user-1");

      assertThat(result).isEmpty();
      verify(userInfoCache, never()).put(any());
    }

    @Test
    void cacheDisabled_directoryResultNotCached() throws Exception {
      when(userInfoCache.isCacheEnabled()).thenReturn(false);
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
      when(directory.byId("user-1")).thenReturn(Optional.empty());

      Optional<UserInfo> result = userService.getUserById("user-1");

      assertThat(result).isEmpty();
      verify(userInfoCache, never()).put(any());
    }
  }

  @Nested
  class DirectoryFailureTests {

    @Test
    void getUserMyself_propagatesDirectoryException() {
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.empty());
      when(directory.byToken("token-1")).thenThrow(new DirectoryException("ldap down", null));

      assertThatThrownBy(() -> userService.getUserMyself("token-1", false))
          .isInstanceOf(DirectoryException.class);
      verify(userMyselfCache, never()).put(any(), any(), any(), anyLong());
    }

    @Test
    void getUserMyself_bypassPropagatesDirectoryExceptionInsteadOfServingStaleEntry() {
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.of(sampleMyself()));
      when(directory.byToken("token-1")).thenThrow(new DirectoryException("ldap down", null));

      assertThatThrownBy(() -> userService.getUserMyself("token-1", true))
          .isInstanceOf(DirectoryException.class);
    }

    @Test
    void getUserById_propagatesDirectoryException() {
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
      when(directory.byId("user-1")).thenThrow(new DirectoryException("ldap down", null));

      assertThatThrownBy(() -> userService.getUserById("user-1"))
          .isInstanceOf(DirectoryException.class);
      verify(userInfoCache, never()).put(any());
    }

    @Test
    void getUserByEmail_propagatesDirectoryException() {
      when(userInfoCache.getByEmail("user@example.com")).thenReturn(Optional.empty());
      when(directory.byEmail("user@example.com"))
          .thenThrow(new DirectoryException("ldap down", null));

      assertThatThrownBy(() -> userService.getUserByEmail("user@example.com"))
          .isInstanceOf(DirectoryException.class);
      verify(userInfoCache, never()).put(any());
    }

    @Test
    void getUserByEmail_propagatesIllegalArgumentException() {
      when(userInfoCache.getByEmail("bad")).thenReturn(Optional.empty());
      when(directory.byEmail("bad")).thenThrow(new IllegalArgumentException("not an email"));

      assertThatThrownBy(() -> userService.getUserByEmail("bad"))
          .isInstanceOf(IllegalArgumentException.class);
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
      verifyNoInteractions(directory);
    }

    @Test
    void cacheMiss_callsDirectory() throws Exception {
      when(userInfoCache.getByEmail("user@example.com")).thenReturn(Optional.empty());
      when(directory.byEmail("user@example.com")).thenReturn(Optional.empty());

      Optional<UserInfo> result = userService.getUserByEmail("user@example.com");

      assertThat(result).isEmpty();
      verify(directory).byEmail("user@example.com");
    }

    @Test
    void directorySuccess_cachesAndReturnsUserInfo() throws Exception {
      when(userInfoCache.getByEmail("user@example.com")).thenReturn(Optional.empty());
      when(directory.byEmail("user@example.com")).thenReturn(Optional.of(sampleUserInfo()));

      Optional<UserInfo> result = userService.getUserByEmail("user@example.com");

      assertThat(result).isPresent();
      assertThat(result.get().email()).isEqualTo("user@example.com");
      assertThat(result.get().cosId()).isEqualTo("cos-1");
      assertThat(result.get().domainId()).isEqualTo("dom-1");
      verify(userInfoCache).put(any(UserInfo.class));
    }

    @Test
    void directoryMiss_returnsEmpty() throws Exception {
      when(userInfoCache.getByEmail("user@example.com")).thenReturn(Optional.empty());
      when(directory.byEmail("user@example.com")).thenReturn(Optional.empty());

      Optional<UserInfo> result = userService.getUserByEmail("user@example.com");

      assertThat(result).isEmpty();
      verify(userInfoCache, never()).put(any());
    }
  }

  @Nested
  class GetUsersTests {

    @Test
    void allCacheHitsSkipsDirectory() {
      UserInfo u1 = sampleUserInfo();
      UserInfo u2 =
          new UserInfo(
              "user-2",
              "u2@example.com",
              "Jane",
              "example.com",
              "ACTIVE",
              "INTERNAL",
              "cos-2",
              "dom-2",
              false);
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.of(u1));
      when(userInfoCache.getByUserId("user-2")).thenReturn(Optional.of(u2));

      List<UserInfo> result = userService.getUsers(List.of("user-1", "user-2"));

      assertThat(result).containsExactly(u1, u2);
      verifyNoInteractions(directory);
    }

    @Test
    void cacheMiss_usesBatchLookup() throws Exception {
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
      when(directory.byIds(List.of("user-1"))).thenReturn(List.of(sampleUserInfo()));

      List<UserInfo> result = userService.getUsers(List.of("user-1"));

      assertThat(result).hasSize(1);
      assertThat(result.get(0).userId()).isEqualTo("user-1");
      assertThat(result.get(0).cosId()).isEqualTo("cos-1");
      assertThat(result.get(0).domainId()).isEqualTo("dom-1");
    }

    @Test
    void deduplicatesUserIds() throws Exception {
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
      when(directory.byIds(List.of("user-1"))).thenReturn(List.of(sampleUserInfo()));

      List<UserInfo> result = userService.getUsers(List.of("user-1", "user-1"));

      assertThat(result).hasSize(1);
    }

    @Test
    void batchFailure_propagatesDirectoryException() {
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
      when(directory.byIds(any())).thenThrow(new DirectoryException("ldap down", null));

      assertThatThrownBy(() -> userService.getUsers(List.of("user-1")))
          .isInstanceOf(DirectoryException.class);
      verify(userInfoCache, never()).put(any());
    }

    @Test
    void onlyMissesAreSentToTheDirectory() {
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.of(sampleUserInfo()));
      when(userInfoCache.getByUserId("user-2")).thenReturn(Optional.empty());
      when(directory.byIds(List.of("user-2"))).thenReturn(List.of());

      userService.getUsers(List.of("user-1", "user-2"));

      verify(directory).byIds(List.of("user-2"));
    }

    @Test
    void returnsOnlyFoundUsers() {
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.of(sampleUserInfo()));
      when(userInfoCache.getByUserId("user-not-found")).thenReturn(Optional.empty());
      when(directory.byIds(List.of("user-not-found"))).thenReturn(List.of());

      List<UserInfo> result = userService.getUsers(List.of("user-1", "user-not-found"));

      assertThat(result).hasSize(1);
      assertThat(result.get(0).userId()).isEqualTo("user-1");
    }

    @Test
    void keepsRequestOrderAcrossCacheHitsAndDirectoryResults() {
      UserInfo u1 = sampleUserInfo();
      UserInfo u2 =
          new UserInfo(
              "user-2", "u2@example.com", "Jane", "example.com", "ACTIVE", "INTERNAL", "cos-2",
              "dom-2", false);
      UserInfo u3 =
          new UserInfo(
              "user-3", "u3@example.com", "Jim", "example.com", "ACTIVE", "INTERNAL", "cos-3",
              "dom-3", false);
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
      when(userInfoCache.getByUserId("user-2")).thenReturn(Optional.of(u2));
      when(userInfoCache.getByUserId("user-3")).thenReturn(Optional.empty());
      when(directory.byIds(List.of("user-1", "user-3"))).thenReturn(List.of(u3, u1));

      List<UserInfo> result = userService.getUsers(List.of("user-1", "user-2", "user-3"));

      assertThat(result).containsExactly(u1, u2, u3);
    }

    @Test
    void emptyInputReturnsEmpty() {
      List<UserInfo> result = userService.getUsers(List.of());
      assertThat(result).isEmpty();
      verifyNoInteractions(directory);
    }
  }
}
