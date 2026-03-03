// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.service;

import static com.zextras.carbonio.user_management.UserManagementServiceConfig.FeatureFlags;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.zextras.carbonio.user_management.cache.UserInfoCache;
import com.zextras.carbonio.user_management.cache.UserMyselfCache;
import com.zextras.carbonio.user_management.record.UserInfo;
import com.zextras.carbonio.user_management.record.UserMyself;
import com.zextras.carbonio.user_management.repository.UserInfoCacheRepository;
import com.zextras.carbonio.user_management.repository.UserMyselfCacheRepository;
import com.zextras.carbonio.user_management.repository.UserMyselfCacheRepository.TokenLookupResult;
import com.zextras.mailbox.client.MailboxClientException;
import com.zextras.mailbox.client.service.ServiceClient;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UserServiceTest {

  private ServiceClient mailboxClient;
  private UserInfoCache userInfoCache;
  private UserMyselfCache userMyselfCache;
  private UserInfoCacheRepository userInfoCacheRepo;
  private UserMyselfCacheRepository userMyselfCacheRepo;
  private UserService userService;

  @BeforeEach
  void setUp() {
    mailboxClient = mock(ServiceClient.class);
    userInfoCache = mock(UserInfoCache.class);
    userMyselfCache = mock(UserMyselfCache.class);
    userInfoCacheRepo = mock(UserInfoCacheRepository.class);
    userMyselfCacheRepo = mock(UserMyselfCacheRepository.class);
    userService = new UserService(
        mailboxClient, userInfoCache, userMyselfCache, userInfoCacheRepo, userMyselfCacheRepo);
  }

  private UserInfo sampleUserInfo() {
    return new UserInfo("user-1", "user@example.com", "John Doe", "example.com", "ACTIVE", "INTERNAL");
  }

  private UserMyself sampleMyself() {
    return new UserMyself(
        "user-1", "user@example.com", "John Doe", "example.com",
        "ACTIVE", "INTERNAL", "en", Map.of(FeatureFlags.FILES_ENABLED, true));
  }

  private long futureExpiresAt() {
    return System.currentTimeMillis() + 3_600_000;
  }

  @Nested
  class GetUserMyselfTests {

    @Test
    void returnsCachedResultWhenL2Hit() {
      UserMyself myself = sampleMyself();
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.of(myself));

      Optional<UserMyself> result = userService.getUserMyself("token-1");

      assertThat(result).contains(myself);
      verify(mailboxClient, never()).send(any());
      // No interaction with UserInfoCache
      verifyNoInteractions(userInfoCache);
      verifyNoInteractions(userInfoCacheRepo);
    }

    @Test
    void callsMailboxWhenTokenNotInCache() throws Exception {
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.empty());
      when(userMyselfCacheRepo.findByToken("token-1")).thenReturn(Optional.empty());
      when(mailboxClient.send(any())).thenThrow(new MailboxClientException("test"));

      Optional<UserMyself> result = userService.getUserMyself("token-1");

      assertThat(result).isEmpty();
      // No interaction with UserInfoCache
      verifyNoInteractions(userInfoCache);
      verifyNoInteractions(userInfoCacheRepo);
    }

    @Test
    void L2Miss_L1Hit_populatesCaffeineNoSoap() {
      UserMyself myself = sampleMyself();
      long expiresAt = futureExpiresAt();

      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.empty());
      when(userMyselfCacheRepo.findByToken("token-1")).thenReturn(
          Optional.of(new TokenLookupResult("user-1", myself, expiresAt)));

      Optional<UserMyself> result = userService.getUserMyself("token-1");

      assertThat(result).contains(myself);
      verify(userMyselfCache).put(eq("token-1"), eq("user-1"), eq(myself), anyLong());
      verify(mailboxClient, never()).send(any());
      // No interaction with UserInfoCache
      verifyNoInteractions(userInfoCache);
      verifyNoInteractions(userInfoCacheRepo);
    }

    @Test
    void L2Miss_L1Miss_callsSoap() throws Exception {
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.empty());
      when(userMyselfCacheRepo.findByToken("token-1")).thenReturn(Optional.empty());
      when(mailboxClient.send(any())).thenThrow(new MailboxClientException("test"));

      Optional<UserMyself> result = userService.getUserMyself("token-1");

      assertThat(result).isEmpty();
      verify(mailboxClient).send(any());
    }

    @Test
    void myselfDoesNotWriteToUserInfoCache() {
      UserMyself myself = sampleMyself();
      long expiresAt = futureExpiresAt();

      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.empty());
      when(userMyselfCacheRepo.findByToken("token-1")).thenReturn(
          Optional.of(new TokenLookupResult("user-1", myself, expiresAt)));

      userService.getUserMyself("token-1");

      // Verify zero interaction with UserInfoCache and its repository
      verifyNoInteractions(userInfoCache);
      verifyNoInteractions(userInfoCacheRepo);
    }
  }

  @Nested
  class GetUserByIdTests {

    @Test
    void returnsCachedResultOnHit() {
      UserInfo info = sampleUserInfo();
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.of(info));

      Optional<UserInfo> result = userService.getUserById("user-1", "token-1");

      assertThat(result).contains(info);
      verifyNoInteractions(userInfoCacheRepo);
      verify(mailboxClient, never()).send(any());
    }

    @Test
    void L2Miss_L1Hit_populatesCaffeineNoSoap() {
      UserInfo info = sampleUserInfo();
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
      when(userInfoCacheRepo.findByUserId("user-1")).thenReturn(Optional.of(info));

      Optional<UserInfo> result = userService.getUserById("user-1", "token-1");

      assertThat(result).contains(info);
      verify(userInfoCache).put(info);
      verify(mailboxClient, never()).send(any());
    }

    @Test
    void L2Miss_L1Miss_callsSoap() throws Exception {
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
      when(userInfoCacheRepo.findByUserId("user-1")).thenReturn(Optional.empty());
      when(mailboxClient.send(any())).thenThrow(new MailboxClientException("test"));

      Optional<UserInfo> result = userService.getUserById("user-1", "token-1");

      assertThat(result).isEmpty();
      verify(mailboxClient).send(any());
    }

    @Test
    void cacheHitIgnoresTokenCompletely() {
      UserInfo info = sampleUserInfo();
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.of(info));

      Optional<UserInfo> result = userService.getUserById("user-1", "fake-or-empty-token");

      assertThat(result).contains(info);
      verify(mailboxClient, never()).send(any());
    }

    @Test
    void failsFastWhenConfigMissing_noSoapCall() {
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
      when(userInfoCacheRepo.findByUserId("user-1")).thenReturn(Optional.empty());
      when(userInfoCache.readTtlSeconds()).thenThrow(
          new IllegalStateException("Missing required config: cache.userinfo-ttl"));

      assertThatThrownBy(() -> userService.getUserById("user-1", "token-1"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("cache.userinfo-ttl");
      verify(mailboxClient, never()).send(any());
    }
  }

  @Nested
  class GetUserByEmailTests {

    @Test
    void returnsCachedResultOnHit() {
      UserInfo info = sampleUserInfo();
      when(userInfoCache.getByEmail("user@example.com")).thenReturn(Optional.of(info));

      Optional<UserInfo> result = userService.getUserByEmail("user@example.com", "token-1");

      assertThat(result).contains(info);
      verifyNoInteractions(userInfoCacheRepo);
      verify(mailboxClient, never()).send(any());
    }

    @Test
    void L2Miss_L1Hit_populatesCaffeineNoSoap() {
      UserInfo info = sampleUserInfo();
      when(userInfoCache.getByEmail("user@example.com")).thenReturn(Optional.empty());
      when(userInfoCacheRepo.findByEmail("user@example.com")).thenReturn(Optional.of(info));

      Optional<UserInfo> result = userService.getUserByEmail("user@example.com", "token-1");

      assertThat(result).contains(info);
      verify(userInfoCache).put(info);
      verify(mailboxClient, never()).send(any());
    }

    @Test
    void returnsEmptyOnMailboxError() throws Exception {
      when(userInfoCache.getByEmail("user@example.com")).thenReturn(Optional.empty());
      when(userInfoCacheRepo.findByEmail("user@example.com")).thenReturn(Optional.empty());
      when(mailboxClient.send(any())).thenThrow(new MailboxClientException("test"));

      Optional<UserInfo> result = userService.getUserByEmail("user@example.com", "token-1");

      assertThat(result).isEmpty();
    }

    @Test
    void cacheHitIgnoresTokenCompletely() {
      UserInfo info = sampleUserInfo();
      when(userInfoCache.getByEmail("user@example.com")).thenReturn(Optional.of(info));

      Optional<UserInfo> result = userService.getUserByEmail("user@example.com", "");

      assertThat(result).contains(info);
      verify(mailboxClient, never()).send(any());
    }

    @Test
    void failsFastWhenConfigMissing_noSoapCall() {
      when(userInfoCache.getByEmail("user@example.com")).thenReturn(Optional.empty());
      when(userInfoCacheRepo.findByEmail("user@example.com")).thenReturn(Optional.empty());
      when(userInfoCache.readTtlSeconds()).thenThrow(
          new IllegalStateException("Missing required config: cache.userinfo-ttl"));

      assertThatThrownBy(() -> userService.getUserByEmail("user@example.com", "token-1"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("cache.userinfo-ttl");
      verify(mailboxClient, never()).send(any());
    }
  }

  @Nested
  class GetUsersTests {

    @Test
    void returnsCachedUsersAndSkipsNotFound() {
      UserInfo user1 = new UserInfo("id-1", "a@x.com", "A", "x.com", "ACTIVE", "INTERNAL");
      UserInfo user2 = new UserInfo("id-2", "b@x.com", "B", "x.com", "ACTIVE", "INTERNAL");

      when(userInfoCache.getByUserId("id-1")).thenReturn(Optional.of(user1));
      when(userInfoCache.getByUserId("id-2")).thenReturn(Optional.of(user2));
      when(userInfoCache.getByUserId("id-3")).thenReturn(Optional.empty());
      when(userInfoCacheRepo.findByUserIds(List.of("id-3"))).thenReturn(List.of());
      when(userInfoCacheRepo.findByUserId("id-3")).thenReturn(Optional.empty());
      when(mailboxClient.send(any())).thenThrow(new MailboxClientException("test"));

      List<UserInfo> result = userService.getUsers(
          List.of("id-1", "id-2", "id-3"), "token-1");

      assertThat(result).containsExactly(user1, user2);
    }

    @Test
    void deduplicatesUserIds() {
      UserInfo user1 = new UserInfo("id-1", "a@x.com", "A", "x.com", "ACTIVE", "INTERNAL");
      when(userInfoCache.getByUserId("id-1")).thenReturn(Optional.of(user1));

      List<UserInfo> result = userService.getUsers(
          List.of("id-1", "id-1", "id-1"), "token-1");

      assertThat(result).hasSize(1);
    }

    @Test
    void batchesL1QueryForL2Misses() {
      UserInfo user1 = new UserInfo("id-1", "a@x.com", "A", "x.com", "ACTIVE", "INTERNAL");
      UserInfo user2 = new UserInfo("id-2", "b@x.com", "B", "x.com", "ACTIVE", "INTERNAL");
      UserInfo user3 = new UserInfo("id-3", "c@x.com", "C", "x.com", "ACTIVE", "INTERNAL");

      when(userInfoCache.getByUserId("id-1")).thenReturn(Optional.of(user1));
      when(userInfoCache.getByUserId("id-2")).thenReturn(Optional.empty());
      when(userInfoCache.getByUserId("id-3")).thenReturn(Optional.empty());
      when(userInfoCacheRepo.findByUserIds(List.of("id-2", "id-3")))
          .thenReturn(List.of(user2, user3));

      List<UserInfo> result = userService.getUsers(
          List.of("id-1", "id-2", "id-3"), "token-1");

      assertThat(result).containsExactly(user1, user2, user3);
      verify(userInfoCacheRepo).findByUserIds(List.of("id-2", "id-3"));
      verify(userInfoCache).put(user2);
      verify(userInfoCache).put(user3);
      verify(mailboxClient, never()).send(any());
    }

    @Test
    void partialL1HitCallsSoapOnlyForRemainingMisses() {
      UserInfo user1 = new UserInfo("id-1", "a@x.com", "A", "x.com", "ACTIVE", "INTERNAL");

      when(userInfoCache.getByUserId("id-1")).thenReturn(Optional.empty());
      when(userInfoCache.getByUserId("id-2")).thenReturn(Optional.empty());
      when(userInfoCacheRepo.findByUserIds(List.of("id-1", "id-2")))
          .thenReturn(List.of(user1));
      when(userInfoCacheRepo.findByUserId("id-2")).thenReturn(Optional.empty());
      when(mailboxClient.send(any())).thenThrow(new MailboxClientException("test"));

      List<UserInfo> result = userService.getUsers(
          List.of("id-1", "id-2"), "token-1");

      assertThat(result).containsExactly(user1);
      verify(userInfoCache).put(user1);
      verify(mailboxClient).send(any());
    }

    @Test
    void allL2HitsSkipsL1AndSoap() {
      UserInfo user1 = new UserInfo("id-1", "a@x.com", "A", "x.com", "ACTIVE", "INTERNAL");
      UserInfo user2 = new UserInfo("id-2", "b@x.com", "B", "x.com", "ACTIVE", "INTERNAL");

      when(userInfoCache.getByUserId("id-1")).thenReturn(Optional.of(user1));
      when(userInfoCache.getByUserId("id-2")).thenReturn(Optional.of(user2));

      List<UserInfo> result = userService.getUsers(
          List.of("id-1", "id-2"), "token-1");

      assertThat(result).containsExactly(user1, user2);
      verifyNoInteractions(userInfoCacheRepo);
      verify(mailboxClient, never()).send(any());
    }
  }
}
