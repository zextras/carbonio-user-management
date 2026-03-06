// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.service;

import static com.zextras.carbonio.user_management.UserManagementServiceConfig.FeatureFlags;
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

import com.zextras.carbonio.user_management.cache.UserInfoCache;
import com.zextras.carbonio.user_management.cache.UserMyselfCache;
import com.zextras.carbonio.user_management.record.UserInfo;
import com.zextras.carbonio.user_management.record.UserMyself;
import com.zextras.carbonio.user_management.repository.UserInfoCacheRepository;
import com.zextras.carbonio.user_management.repository.UserMyselfCacheRepository;
import com.zextras.carbonio.user_management.repository.UserMyselfCacheRepository.TokenLookupResult;
import com.zextras.carbonio.user_management.service.UserService;
import com.zextras.mailbox.client.MailboxClientException;
import com.zextras.mailbox.client.service.ServiceClient;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import zimbra.NamedValue;
import zimbraaccount.Attr;
import zimbraaccount.GetAccountInfoResponse;
import zimbraaccount.GetInfoResponse;

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
    when(userInfoCache.isCacheEnabled()).thenReturn(true);
    when(userMyselfCache.isCacheEnabled()).thenReturn(true);
    userService = new UserService(
        mailboxClient, userInfoCache, userMyselfCache, userInfoCacheRepo, userMyselfCacheRepo,
        org.eclipse.microprofile.context.ManagedExecutor.builder().build());
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
    return 3_600_000L;
  }

  @Nested
  class GetUserMyselfTests {

    @Test
    void returnsCachedResultWhenL2Hit() {
      UserMyself myself = sampleMyself();
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.of(myself));
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.of(sampleUserInfo()));

      Optional<UserMyself> result = userService.getUserMyself("token-1");

      assertThat(result).contains(myself);
      verify(mailboxClient, never()).send(any());
    }

    @Test
    void callsMailboxWhenTokenNotInCache() throws Exception {
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.empty());
      when(userMyselfCacheRepo.findByToken("token-1")).thenReturn(Optional.empty());
      when(mailboxClient.send(any())).thenThrow(new MailboxClientException("test"));

      Optional<UserMyself> result = userService.getUserMyself("token-1");

      assertThat(result).isEmpty();
      // SOAP failed → Optional.empty() → ifPresent does not execute warming
      verify(userInfoCache, never()).getByUserId(any());
    }

    @Test
    void L2Miss_L1Hit_populatesCaffeineNoSoap() {
      UserMyself myself = sampleMyself();
      long expiresAt = futureExpiresAt();

      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.empty());
      when(userMyselfCacheRepo.findByToken("token-1")).thenReturn(
          Optional.of(new TokenLookupResult("user-1", myself, expiresAt)));
      // Warming: L2 userinfo miss → L1 userinfo miss → writes both
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
      when(userInfoCacheRepo.findByUserId("user-1")).thenReturn(Optional.empty());
      when(userInfoCache.computeExpiresAt()).thenReturn(futureExpiresAt());

      Optional<UserMyself> result = userService.getUserMyself("token-1");

      assertThat(result).contains(myself);
      verify(userMyselfCache).put(eq("token-1"), eq("user-1"), eq(myself), anyLong());
      verify(mailboxClient, never()).send(any());
    }

    @Test
    void L2Miss_L1Miss_callsSoap() throws Exception {
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.empty());
      when(userMyselfCacheRepo.findByToken("token-1")).thenReturn(Optional.empty());
      when(mailboxClient.send(any())).thenThrow(new MailboxClientException("test"));

      Optional<UserMyself> result = userService.getUserMyself("token-1");

      assertThat(result).isEmpty();
      verify(mailboxClient).send(any());
      // SOAP failed → Optional.empty() → no warming
      verify(userInfoCache, never()).getByUserId(any());
    }

    @Test
    void L2Hit_warmsOnlyIfAbsent_skipsWhenUserInfoExists() {
      UserMyself myself = sampleMyself();
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.of(myself));
      // Warming: L2 userinfo hit → skip
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.of(sampleUserInfo()));

      userService.getUserMyself("token-1");

      // Should not check L1 or write anything
      verify(userInfoCacheRepo, never()).findByUserId(any());
      verify(userInfoCacheRepo, never()).upsert(any(), anyLong());
      verify(userInfoCache, never()).put(any(UserInfo.class));
    }

    @Test
    void warmingWritesToBothLayersWhenUserInfoAbsent() {
      UserMyself myself = sampleMyself();
      long expiresAt = futureExpiresAt();
      UserInfo expectedUserInfo = new UserInfo(
          "user-1", "user@example.com", "John Doe", "example.com", "ACTIVE", "INTERNAL");

      // L1 hit myself
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.empty());
      when(userMyselfCacheRepo.findByToken("token-1")).thenReturn(
          Optional.of(new TokenLookupResult("user-1", myself, expiresAt)));
      // Warming: L2 miss → L1 miss → write both
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
      when(userInfoCacheRepo.findByUserId("user-1")).thenReturn(Optional.empty());
      when(userInfoCache.computeExpiresAt()).thenReturn(futureExpiresAt());
      when(userInfoCacheRepo.upsert(eq(expectedUserInfo), anyLong())).thenReturn(expectedUserInfo);

      Optional<UserMyself> result = userService.getUserMyself("token-1");

      assertThat(result).contains(myself);
      verify(userInfoCacheRepo).upsert(eq(expectedUserInfo), anyLong());
      verify(userInfoCache).put(expectedUserInfo);
    }

    @Test
    void warmingSkipsWhenUserInfoInL1Only() {
      UserMyself myself = sampleMyself();
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.of(myself));
      // Warming: L2 miss → L1 hit → skip
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
      when(userInfoCacheRepo.findByUserId("user-1")).thenReturn(Optional.of(sampleUserInfo()));

      Optional<UserMyself> result = userService.getUserMyself("token-1");

      assertThat(result).contains(myself);
      verify(userInfoCacheRepo, never()).upsert(any(), anyLong());
      verify(userInfoCache, never()).put(any(UserInfo.class));
    }

    @Test
    void warmingFailureDoesNotPropagateAndReturnsResult() {
      UserMyself myself = sampleMyself();
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.of(myself));
      when(userInfoCache.getByUserId("user-1")).thenThrow(new RuntimeException("db error"));

      Optional<UserMyself> result = userService.getUserMyself("token-1");

      assertThat(result).contains(myself);
    }

    @Test
    void L1ReadFailure_fallsToSoap() throws Exception {
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.empty());
      when(userMyselfCacheRepo.findByToken("token-1")).thenThrow(new RuntimeException("db error"));
      when(mailboxClient.send(any())).thenThrow(new MailboxClientException("test"));

      Optional<UserMyself> result = userService.getUserMyself("token-1");

      assertThat(result).isEmpty();
      verify(mailboxClient).send(any());
    }

    @Test
    void L1WriteFailure_stillReturnsDataAndCachesL2() throws Exception {
      UserMyself myself = sampleMyself();
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.empty());
      when(userMyselfCacheRepo.findByToken("token-1")).thenReturn(Optional.empty());

      GetInfoResponse response = mock(GetInfoResponse.class);
      GetInfoResponse.Attrs attrs = mock(GetInfoResponse.Attrs.class);
      when(response.getAttrs()).thenReturn(attrs);
      Attr idAttr = mock(Attr.class);
      when(idAttr.getName()).thenReturn("zimbraId");
      when(idAttr.getValue()).thenReturn("user-1");
      Attr nameAttr = mock(Attr.class);
      when(nameAttr.getName()).thenReturn("displayName");
      when(nameAttr.getValue()).thenReturn("John Doe");
      when(attrs.getAttr()).thenReturn(List.of(idAttr, nameAttr));
      when(response.getName()).thenReturn("user@example.com");
      when(response.getPublicURL()).thenReturn("example.com");
      when(response.getLifetime()).thenReturn(3600000L);
      when(mailboxClient.send(any())).thenReturn(response);
      when(userMyselfCache.computeExpiresAt(3600000L)).thenReturn(futureExpiresAt());
      when(userMyselfCacheRepo.upsert(any(), any(), any(), anyLong()))
          .thenThrow(new RuntimeException("db write error"));
      // Warming setup
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
      when(userInfoCacheRepo.findByUserId("user-1")).thenReturn(Optional.empty());
      when(userInfoCache.computeExpiresAt()).thenReturn(futureExpiresAt());

      Optional<UserMyself> result = userService.getUserMyself("token-1");

      assertThat(result).isPresent();
      assertThat(result.get().userId()).isEqualTo("user-1");
      verify(userMyselfCache).put(eq("token-1"), eq("user-1"), any(), anyLong());
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
    void L1ReadFailure_fallsToSoap() throws Exception {
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
      when(userInfoCacheRepo.findByUserId("user-1")).thenThrow(new RuntimeException("db error"));
      when(mailboxClient.send(any())).thenThrow(new MailboxClientException("test"));

      Optional<UserInfo> result = userService.getUserById("user-1", "token-1");

      assertThat(result).isEmpty();
      verify(mailboxClient).send(any());
    }

    @Test
    void L1WriteFailure_stillReturnsDataAndCachesL2() throws Exception {
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
      when(userInfoCacheRepo.findByUserId("user-1")).thenReturn(Optional.empty());

      GetAccountInfoResponse response = mock(GetAccountInfoResponse.class);
      NamedValue idAttr = mock(NamedValue.class);
      when(idAttr.getName()).thenReturn("zimbraId");
      when(idAttr.getValue()).thenReturn("user-1");
      NamedValue nameAttr = mock(NamedValue.class);
      when(nameAttr.getName()).thenReturn("displayName");
      when(nameAttr.getValue()).thenReturn("John Doe");
      when(response.getAttr()).thenReturn(List.of(idAttr, nameAttr));
      when(response.getName()).thenReturn("user@example.com");
      when(response.getPublicURL()).thenReturn("example.com");
      when(mailboxClient.send(any())).thenReturn(response);
      when(userInfoCache.computeExpiresAt()).thenReturn(futureExpiresAt());
      when(userInfoCacheRepo.upsert(any(), anyLong()))
          .thenThrow(new RuntimeException("db write error"));

      Optional<UserInfo> result = userService.getUserById("user-1", "token-1");

      assertThat(result).isPresent();
      assertThat(result.get().userId()).isEqualTo("user-1");
      verify(userInfoCache).put(any(UserInfo.class));
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
    void L1ReadFailure_fallsToSoap() throws Exception {
      when(userInfoCache.getByEmail("user@example.com")).thenReturn(Optional.empty());
      when(userInfoCacheRepo.findByEmail("user@example.com"))
          .thenThrow(new RuntimeException("db error"));
      when(mailboxClient.send(any())).thenThrow(new MailboxClientException("test"));

      Optional<UserInfo> result = userService.getUserByEmail("user@example.com", "token-1");

      assertThat(result).isEmpty();
      verify(mailboxClient).send(any());
    }

    @Test
    void L1WriteFailure_stillReturnsDataAndCachesL2() throws Exception {
      when(userInfoCache.getByEmail("user@example.com")).thenReturn(Optional.empty());
      when(userInfoCacheRepo.findByEmail("user@example.com")).thenReturn(Optional.empty());

      GetAccountInfoResponse response = mock(GetAccountInfoResponse.class);
      NamedValue idAttr = mock(NamedValue.class);
      when(idAttr.getName()).thenReturn("zimbraId");
      when(idAttr.getValue()).thenReturn("user-1");
      NamedValue nameAttr = mock(NamedValue.class);
      when(nameAttr.getName()).thenReturn("displayName");
      when(nameAttr.getValue()).thenReturn("John Doe");
      when(response.getAttr()).thenReturn(List.of(idAttr, nameAttr));
      when(response.getName()).thenReturn("user@example.com");
      when(response.getPublicURL()).thenReturn("example.com");
      when(mailboxClient.send(any())).thenReturn(response);
      when(userInfoCache.computeExpiresAt()).thenReturn(futureExpiresAt());
      when(userInfoCacheRepo.upsert(any(), anyLong()))
          .thenThrow(new RuntimeException("db write error"));

      Optional<UserInfo> result = userService.getUserByEmail("user@example.com", "token-1");

      assertThat(result).isPresent();
      assertThat(result.get().userId()).isEqualTo("user-1");
      verify(userInfoCache).put(any(UserInfo.class));
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

    @Test
    void L1BatchReadFailure_allMissesGoToSoap() {
      when(userInfoCache.getByUserId("id-1")).thenReturn(Optional.empty());
      when(userInfoCache.getByUserId("id-2")).thenReturn(Optional.empty());
      when(userInfoCacheRepo.findByUserIds(List.of("id-1", "id-2")))
          .thenThrow(new RuntimeException("db error"));
      // L1 read in getUserById also fails
      when(userInfoCacheRepo.findByUserId(any())).thenThrow(new RuntimeException("db error"));
      when(mailboxClient.send(any())).thenThrow(new MailboxClientException("test"));

      List<UserInfo> result = userService.getUsers(
          List.of("id-1", "id-2"), "token-1");

      assertThat(result).isEmpty();
      // SOAP called for each miss
      verify(mailboxClient, times(2)).send(any());
    }
  }

  @Nested
  class CacheDisabledTests {

    @Test
    void userInfoTtlZero_getByIdCallsSoapAndNeverPersists() throws Exception {
      when(userInfoCache.isCacheEnabled()).thenReturn(false);
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
      when(userInfoCacheRepo.findByUserId("user-1")).thenReturn(Optional.empty());

      GetAccountInfoResponse response = mock(GetAccountInfoResponse.class);
      NamedValue idAttr = mock(NamedValue.class);
      when(idAttr.getName()).thenReturn("zimbraId");
      when(idAttr.getValue()).thenReturn("user-1");
      NamedValue nameAttr = mock(NamedValue.class);
      when(nameAttr.getName()).thenReturn("displayName");
      when(nameAttr.getValue()).thenReturn("John Doe");
      when(response.getAttr()).thenReturn(List.of(idAttr, nameAttr));
      when(response.getName()).thenReturn("user@example.com");
      when(response.getPublicURL()).thenReturn("example.com");
      when(mailboxClient.send(any())).thenReturn(response);

      Optional<UserInfo> result = userService.getUserById("user-1", "token-1");

      assertThat(result).isPresent();
      verify(mailboxClient).send(any());
      verify(userInfoCacheRepo, never()).upsert(any(), anyLong());
      verify(userInfoCache, never()).put(any(UserInfo.class));
    }

    @Test
    void userInfoTtlZero_getByEmailCallsSoapAndNeverPersists() throws Exception {
      when(userInfoCache.isCacheEnabled()).thenReturn(false);
      when(userInfoCache.getByEmail("user@example.com")).thenReturn(Optional.empty());
      when(userInfoCacheRepo.findByEmail("user@example.com")).thenReturn(Optional.empty());

      GetAccountInfoResponse response = mock(GetAccountInfoResponse.class);
      NamedValue idAttr = mock(NamedValue.class);
      when(idAttr.getName()).thenReturn("zimbraId");
      when(idAttr.getValue()).thenReturn("user-1");
      NamedValue nameAttr = mock(NamedValue.class);
      when(nameAttr.getName()).thenReturn("displayName");
      when(nameAttr.getValue()).thenReturn("John Doe");
      when(response.getAttr()).thenReturn(List.of(idAttr, nameAttr));
      when(response.getName()).thenReturn("user@example.com");
      when(response.getPublicURL()).thenReturn("example.com");
      when(mailboxClient.send(any())).thenReturn(response);

      Optional<UserInfo> result = userService.getUserByEmail("user@example.com", "token-1");

      assertThat(result).isPresent();
      verify(mailboxClient).send(any());
      verify(userInfoCacheRepo, never()).upsert(any(), anyLong());
      verify(userInfoCache, never()).put(any(UserInfo.class));
    }

    @Test
    void userMyselfTtlZero_callsSoapAndNeverPersistsMyself() throws Exception {
      when(userMyselfCache.isCacheEnabled()).thenReturn(false);
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.empty());
      when(userMyselfCacheRepo.findByToken("token-1")).thenReturn(Optional.empty());

      GetInfoResponse response = mock(GetInfoResponse.class);
      GetInfoResponse.Attrs attrs = mock(GetInfoResponse.Attrs.class);
      when(response.getAttrs()).thenReturn(attrs);
      Attr idAttr = mock(Attr.class);
      when(idAttr.getName()).thenReturn("zimbraId");
      when(idAttr.getValue()).thenReturn("user-1");
      Attr nameAttr = mock(Attr.class);
      when(nameAttr.getName()).thenReturn("displayName");
      when(nameAttr.getValue()).thenReturn("John Doe");
      when(attrs.getAttr()).thenReturn(List.of(idAttr, nameAttr));
      when(response.getName()).thenReturn("user@example.com");
      when(response.getPublicURL()).thenReturn("example.com");
      when(response.getLifetime()).thenReturn(3600000L);
      when(mailboxClient.send(any())).thenReturn(response);
      when(userMyselfCache.computeExpiresAt(3600000L)).thenReturn(3_600_000L);
      // Warming: L2 userinfo hit → no persist needed
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.of(sampleUserInfo()));

      Optional<UserMyself> result = userService.getUserMyself("token-1");

      assertThat(result).isPresent();
      verify(mailboxClient).send(any());
      verify(userMyselfCacheRepo, never()).upsert(any(), any(), any(), anyLong());
      verify(userMyselfCache, never()).put(any(), any(), any(), anyLong());
    }

    @Test
    void bothTtlZero_getMyselfNeverPersistsAnything() throws Exception {
      when(userInfoCache.isCacheEnabled()).thenReturn(false);
      when(userMyselfCache.isCacheEnabled()).thenReturn(false);
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.empty());
      when(userMyselfCacheRepo.findByToken("token-1")).thenReturn(Optional.empty());

      GetInfoResponse response = mock(GetInfoResponse.class);
      GetInfoResponse.Attrs attrs = mock(GetInfoResponse.Attrs.class);
      when(response.getAttrs()).thenReturn(attrs);
      Attr idAttr = mock(Attr.class);
      when(idAttr.getName()).thenReturn("zimbraId");
      when(idAttr.getValue()).thenReturn("user-1");
      Attr nameAttr = mock(Attr.class);
      when(nameAttr.getName()).thenReturn("displayName");
      when(nameAttr.getValue()).thenReturn("John Doe");
      when(attrs.getAttr()).thenReturn(List.of(idAttr, nameAttr));
      when(response.getName()).thenReturn("user@example.com");
      when(response.getPublicURL()).thenReturn("example.com");
      when(response.getLifetime()).thenReturn(3600000L);
      when(mailboxClient.send(any())).thenReturn(response);
      when(userMyselfCache.computeExpiresAt(3600000L)).thenReturn(3_600_000L);
      // Warming: L2 miss → L1 miss → persistAndCacheInfo skipped (disabled)
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
      when(userInfoCacheRepo.findByUserId("user-1")).thenReturn(Optional.empty());

      Optional<UserMyself> result = userService.getUserMyself("token-1");

      assertThat(result).isPresent();
      verify(mailboxClient).send(any());
      // Myself: no persist
      verify(userMyselfCacheRepo, never()).upsert(any(), any(), any(), anyLong());
      verify(userMyselfCache, never()).put(any(), any(), any(), anyLong());
      // UserInfo: warming ran but persist skipped
      verify(userInfoCache).getByUserId("user-1");
      verify(userInfoCacheRepo).findByUserId("user-1");
      verify(userInfoCacheRepo, never()).upsert(any(), anyLong());
      verify(userInfoCache, never()).put(any(UserInfo.class));
    }
  }

  @Nested
  class WarmingL1FailureTests {

    @Test
    void warmingL1ReadFailure_stillWritesL2() {
      UserMyself myself = sampleMyself();
      UserInfo expectedUserInfo = new UserInfo(
          "user-1", "user@example.com", "John Doe", "example.com", "ACTIVE", "INTERNAL");

      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.of(myself));
      // L2 userinfo miss
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
      // L1 read fails
      when(userInfoCacheRepo.findByUserId("user-1")).thenThrow(new RuntimeException("db error"));
      when(userInfoCache.computeExpiresAt()).thenReturn(futureExpiresAt());
      // L1 write also fails (both try-catches exercised)
      when(userInfoCacheRepo.upsert(eq(expectedUserInfo), anyLong()))
          .thenThrow(new RuntimeException("db write error"));

      Optional<UserMyself> result = userService.getUserMyself("token-1");

      assertThat(result).contains(myself);
      // L2 write still happened
      verify(userInfoCache).put(expectedUserInfo);
    }

    @Test
    void warmingL1WriteFailure_stillWritesL2() {
      UserMyself myself = sampleMyself();
      UserInfo expectedUserInfo = new UserInfo(
          "user-1", "user@example.com", "John Doe", "example.com", "ACTIVE", "INTERNAL");

      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.of(myself));
      // L2 userinfo miss
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
      // L1 read ok → miss
      when(userInfoCacheRepo.findByUserId("user-1")).thenReturn(Optional.empty());
      when(userInfoCache.computeExpiresAt()).thenReturn(futureExpiresAt());
      // L1 write fails
      when(userInfoCacheRepo.upsert(eq(expectedUserInfo), anyLong()))
          .thenThrow(new RuntimeException("db write error"));

      Optional<UserMyself> result = userService.getUserMyself("token-1");

      assertThat(result).contains(myself);
      // L2 write still happened
      verify(userInfoCache).put(expectedUserInfo);
    }
  }
}
