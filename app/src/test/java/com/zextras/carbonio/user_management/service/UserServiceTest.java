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

import com.zextras.carbonio.user_management.cache.UserDetailsCache;
import com.zextras.carbonio.user_management.cache.UserInfoCache;
import com.zextras.carbonio.user_management.cache.record.UserDetails;
import com.zextras.carbonio.user_management.cache.record.UserInfo;
import com.zextras.carbonio.user_management.cache.repository.UserDetailsCacheRepository;
import com.zextras.carbonio.user_management.cache.repository.UserDetailsCacheRepository.CachedUserDetails;
import com.zextras.carbonio.user_management.cache.repository.UserDetailsCacheRepository.TokenLookupResult;
import com.zextras.carbonio.user_management.cache.repository.UserInfoCacheRepository;
import com.zextras.carbonio.user_management.service.UserService.MyselfResult;
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
  private UserDetailsCache userDetailsCache;
  private UserInfoCacheRepository userInfoCacheRepo;
  private UserDetailsCacheRepository userDetailsCacheRepo;
  private UserService userService;

  @BeforeEach
  void setUp() {
    mailboxClient = mock(ServiceClient.class);
    userInfoCache = mock(UserInfoCache.class);
    userDetailsCache = mock(UserDetailsCache.class);
    userInfoCacheRepo = mock(UserInfoCacheRepository.class);
    userDetailsCacheRepo = mock(UserDetailsCacheRepository.class);
    userService = new UserService(
        mailboxClient, userInfoCache, userDetailsCache, userInfoCacheRepo, userDetailsCacheRepo);
  }

  private UserInfo sampleUserInfo() {
    return new UserInfo("user-1", "user@example.com", "John Doe", "example.com", "ACTIVE", "INTERNAL");
  }

  private UserDetails sampleDetails() {
    return new UserDetails("en", Map.of(FeatureFlags.FILES_ENABLED, true));
  }

  private long futureExpiresAt() {
    return System.currentTimeMillis() + 3_600_000;
  }

  @Nested
  class GetUserMyselfTests {

    @Test
    void returnsCachedResultWhenBothCachesHit() {
      UserInfo info = sampleUserInfo();
      UserDetails details = sampleDetails();

      when(userDetailsCache.getByToken("token-1")).thenReturn(Optional.of(details));
      when(userDetailsCache.resolveUserId("token-1")).thenReturn(Optional.of("user-1"));
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.of(info));

      Optional<MyselfResult> result = userService.getUserMyself("token-1");

      assertThat(result).isPresent();
      assertThat(result.get().info()).isEqualTo(info);
      assertThat(result.get().details()).isEqualTo(details);
      verify(mailboxClient, never()).send(any());
    }

    @Test
    void callsMailboxWhenTokenNotInCache() throws Exception {
      when(userDetailsCache.getByToken("token-1")).thenReturn(Optional.empty());
      when(userDetailsCache.resolveUserId("token-1")).thenReturn(Optional.empty());
      when(userDetailsCacheRepo.findByToken("token-1")).thenReturn(Optional.empty());
      when(mailboxClient.send(any())).thenThrow(new MailboxClientException("test"));

      Optional<MyselfResult> result = userService.getUserMyself("token-1");

      assertThat(result).isEmpty();
    }

    @Test
    void callsMailboxWhenDetailsMissButUserIdResolved() throws Exception {
      UserInfo info = sampleUserInfo();

      // L2: details miss, userId resolved (stale tokenToUserId entry)
      when(userDetailsCache.getByToken("token-1")).thenReturn(Optional.empty());
      when(userDetailsCache.resolveUserId("token-1")).thenReturn(Optional.of("user-1"));
      // In loadUserMyself: userId known, L2 info hit, L1 details miss
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.of(info));
      when(userDetailsCacheRepo.findByUserId("user-1")).thenReturn(Optional.empty());
      // SOAP fails
      when(mailboxClient.send(any())).thenThrow(new MailboxClientException("test"));

      Optional<MyselfResult> result = userService.getUserMyself("token-1");

      assertThat(result).isEmpty();
    }

    @Test
    void L2Miss_L1Hit_resolvesTokenAndReturns() {
      UserInfo info = sampleUserInfo();
      UserDetails details = sampleDetails();
      long expiresAt = futureExpiresAt();

      // L2 miss
      when(userDetailsCache.getByToken("token-1")).thenReturn(Optional.empty());
      when(userDetailsCache.resolveUserId("token-1")).thenReturn(Optional.empty());
      // L1 resolves token -> userId + details
      when(userDetailsCacheRepo.findByToken("token-1")).thenReturn(
          Optional.of(new TokenLookupResult("user-1", details, expiresAt)));
      // L2 miss for info
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
      // L1 has info
      when(userInfoCacheRepo.findByUserId("user-1")).thenReturn(Optional.of(info));

      Optional<MyselfResult> result = userService.getUserMyself("token-1");

      assertThat(result).isPresent();
      assertThat(result.get().info()).isEqualTo(info);
      assertThat(result.get().details()).isEqualTo(details);
      verify(userInfoCache).put(info);
      verify(userDetailsCache).put(eq("token-1"), eq("user-1"), eq(details), anyLong());
      verify(mailboxClient, never()).send(any());
    }

    @Test
    void L2ResolveHit_L1DetailsHit() {
      UserInfo info = sampleUserInfo();
      UserDetails details = sampleDetails();
      long expiresAt = futureExpiresAt();

      // L2: details miss, userId resolved
      when(userDetailsCache.getByToken("token-1")).thenReturn(Optional.empty());
      when(userDetailsCache.resolveUserId("token-1")).thenReturn(Optional.of("user-1"));
      // In loadUserMyself: info from L2
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.of(info));
      // L1 has details
      when(userDetailsCacheRepo.findByUserId("user-1")).thenReturn(
          Optional.of(new CachedUserDetails(details, expiresAt)));

      Optional<MyselfResult> result = userService.getUserMyself("token-1");

      assertThat(result).isPresent();
      assertThat(result.get().details()).isEqualTo(details);
      verify(mailboxClient, never()).send(any());
    }

    @Test
    void L2Miss_L1Miss_callsSoap() throws Exception {
      when(userDetailsCache.getByToken("token-1")).thenReturn(Optional.empty());
      when(userDetailsCache.resolveUserId("token-1")).thenReturn(Optional.empty());
      when(userDetailsCacheRepo.findByToken("token-1")).thenReturn(Optional.empty());
      when(mailboxClient.send(any())).thenThrow(new MailboxClientException("test"));

      Optional<MyselfResult> result = userService.getUserMyself("token-1");

      assertThat(result).isEmpty();
      verify(mailboxClient).send(any());
    }

    @Test
    void failsFastWhenConfigMissing_noSoapCall() {
      when(userDetailsCache.getByToken("token-1")).thenReturn(Optional.empty());
      when(userDetailsCache.resolveUserId("token-1")).thenReturn(Optional.empty());
      when(userDetailsCacheRepo.findByToken("token-1")).thenReturn(Optional.empty());
      when(userInfoCache.readTtlSeconds()).thenThrow(
          new IllegalStateException("Missing required config: cache.userinfo-ttl"));

      assertThatThrownBy(() -> userService.getUserMyself("token-1"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("cache.userinfo-ttl");
      verify(mailboxClient, never()).send(any());
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
  }

  @Nested
  class GetUsersTests {

    @Test
    void returnsCachedUsersAndSkipsNotFound() {
      UserInfo user1 = new UserInfo("id-1", "a@x.com", "A", "x.com", "ACTIVE", "INTERNAL");
      UserInfo user2 = new UserInfo("id-2", "b@x.com", "B", "x.com", "ACTIVE", "INTERNAL");

      // L2 hits for id-1 and id-2
      when(userInfoCache.getByUserId("id-1")).thenReturn(Optional.of(user1));
      when(userInfoCache.getByUserId("id-2")).thenReturn(Optional.of(user2));
      // L2 miss for id-3
      when(userInfoCache.getByUserId("id-3")).thenReturn(Optional.empty());
      // L1 batch miss for id-3
      when(userInfoCacheRepo.findByUserIds(List.of("id-3"))).thenReturn(List.of());
      // SOAP fallback (getUserById → L2 miss → L1 miss → SOAP fails)
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

      // L2 hit for id-1 only
      when(userInfoCache.getByUserId("id-1")).thenReturn(Optional.of(user1));
      when(userInfoCache.getByUserId("id-2")).thenReturn(Optional.empty());
      when(userInfoCache.getByUserId("id-3")).thenReturn(Optional.empty());
      // L1 batch returns id-2 and id-3
      when(userInfoCacheRepo.findByUserIds(List.of("id-2", "id-3")))
          .thenReturn(List.of(user2, user3));

      List<UserInfo> result = userService.getUsers(
          List.of("id-1", "id-2", "id-3"), "token-1");

      assertThat(result).containsExactly(user1, user2, user3);
      // L1 batch query was used (single query, not individual queries)
      verify(userInfoCacheRepo).findByUserIds(List.of("id-2", "id-3"));
      // L2 was populated from L1 hits
      verify(userInfoCache).put(user2);
      verify(userInfoCache).put(user3);
      // No SOAP calls needed
      verify(mailboxClient, never()).send(any());
    }

    @Test
    void partialL1HitCallsSoapOnlyForRemainingMisses() {
      UserInfo user1 = new UserInfo("id-1", "a@x.com", "A", "x.com", "ACTIVE", "INTERNAL");
      UserInfo user2 = new UserInfo("id-2", "b@x.com", "B", "x.com", "ACTIVE", "INTERNAL");

      // All L2 misses
      when(userInfoCache.getByUserId("id-1")).thenReturn(Optional.empty());
      when(userInfoCache.getByUserId("id-2")).thenReturn(Optional.empty());
      // L1 batch returns only id-1
      when(userInfoCacheRepo.findByUserIds(List.of("id-1", "id-2")))
          .thenReturn(List.of(user1));
      // SOAP fallback for id-2 (getUserById re-checks L2 & L1 individually, then SOAP)
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
