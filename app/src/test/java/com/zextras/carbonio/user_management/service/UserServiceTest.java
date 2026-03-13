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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.zextras.carbonio.user_management.cache.UserInfoCache;
import com.zextras.carbonio.user_management.cache.UserMyselfCache;
import com.zextras.carbonio.user_management.record.UserInfo;
import com.zextras.carbonio.user_management.record.UserMyself;
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
  private UserService userService;

  @BeforeEach
  void setUp() {
    mailboxClient = mock(ServiceClient.class);
    userInfoCache = mock(UserInfoCache.class);
    userMyselfCache = mock(UserMyselfCache.class);
    when(userInfoCache.isCacheEnabled()).thenReturn(true);
    when(userMyselfCache.isCacheEnabled()).thenReturn(true);
    userService = new UserService(
        mailboxClient, userInfoCache, userMyselfCache,
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
    return System.currentTimeMillis() + 3_600_000L;
  }

  @Nested
  class GetUserMyselfTests {

    @Test
    void returnsCachedResultWhenCacheHit() {
      UserMyself myself = sampleMyself();
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.of(myself));
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.of(sampleUserInfo()));

      Optional<UserMyself> result = userService.getUserMyself("token-1");

      assertThat(result).contains(myself);
      verify(mailboxClient, never()).send(any());
    }

    @Test
    void cacheMiss_callsSoap() throws Exception {
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.empty());
      when(mailboxClient.send(any())).thenThrow(new MailboxClientException("test"));

      Optional<UserMyself> result = userService.getUserMyself("token-1");

      assertThat(result).isEmpty();
      verify(mailboxClient).send(any());
    }

    @Test
    void cacheHit_warmsUserInfoIfAbsent() {
      UserMyself myself = sampleMyself();
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.of(myself));
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());

      userService.getUserMyself("token-1");

      verify(userInfoCache).put(any(UserInfo.class));
    }

    @Test
    void cacheHit_skipsWarmingWhenUserInfoAlreadyCached() {
      UserMyself myself = sampleMyself();
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.of(myself));
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.of(sampleUserInfo()));

      userService.getUserMyself("token-1");

      verify(userInfoCache, never()).put(any());
    }

    @Test
    void soapSuccess_cachesResultAndReturnsMyself() throws Exception {
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.empty());

      GetInfoResponse response = mock(GetInfoResponse.class);
      GetInfoResponse.Attrs attrs = mock(GetInfoResponse.Attrs.class);
      Attr idAttr = mock(Attr.class);
      when(idAttr.getName()).thenReturn("zimbraId");
      when(idAttr.getValue()).thenReturn("user-1");
      Attr nameAttr = mock(Attr.class);
      when(nameAttr.getName()).thenReturn("displayName");
      when(nameAttr.getValue()).thenReturn("John Doe");
      when(attrs.getAttr()).thenReturn(List.of(idAttr, nameAttr));
      when(response.getAttrs()).thenReturn(attrs);
      when(response.getName()).thenReturn("user@example.com");
      when(response.getPublicURL()).thenReturn("example.com");
      when(response.getLifetime()).thenReturn(3_600_000L);
      when(response.getPrefs()).thenReturn(null);
      when(mailboxClient.send(any())).thenReturn(response);
      when(userMyselfCache.computeExpiresAt(anyLong())).thenReturn(futureExpiresAt());
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());

      Optional<UserMyself> result = userService.getUserMyself("token-1");

      assertThat(result).isPresent();
      assertThat(result.get().userId()).isEqualTo("user-1");
      verify(userMyselfCache).put(eq("token-1"), eq("user-1"), any(), anyLong());
    }

    @Test
    void soapFailure_returnsEmpty() throws Exception {
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.empty());
      when(mailboxClient.send(any())).thenThrow(new MailboxClientException("test"));

      Optional<UserMyself> result = userService.getUserMyself("token-1");

      assertThat(result).isEmpty();
      verify(userMyselfCache, never()).put(any(), any(), any(), anyLong());
    }

    @Test
    void cacheDisabled_soapResultNotCached() throws Exception {
      when(userMyselfCache.isCacheEnabled()).thenReturn(false);
      when(userInfoCache.isCacheEnabled()).thenReturn(false);
      when(userMyselfCache.getByToken("token-1")).thenReturn(Optional.empty());
      when(mailboxClient.send(any())).thenThrow(new MailboxClientException("test"));

      Optional<UserMyself> result = userService.getUserMyself("token-1");

      assertThat(result).isEmpty();
      verify(userMyselfCache, never()).put(any(), any(), any(), anyLong());
    }
  }

  @Nested
  class GetUserByIdTests {

    @Test
    void returnsCachedResultWhenCacheHit() {
      UserInfo userInfo = sampleUserInfo();
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.of(userInfo));

      Optional<UserInfo> result = userService.getUserById("user-1", "token-1");

      assertThat(result).contains(userInfo);
      verify(mailboxClient, never()).send(any());
    }

    @Test
    void cacheMiss_callsSoap() throws Exception {
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
      when(mailboxClient.send(any())).thenThrow(new MailboxClientException("test"));

      Optional<UserInfo> result = userService.getUserById("user-1", "token-1");

      assertThat(result).isEmpty();
      verify(mailboxClient).send(any());
    }

    @Test
    void soapSuccess_cachesAndReturnsUserInfo() throws Exception {
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());

      GetAccountInfoResponse response = mockAccountInfoResponse("user-1", "user@example.com");
      when(mailboxClient.send(any())).thenReturn(response);

      Optional<UserInfo> result = userService.getUserById("user-1", "token-1");

      assertThat(result).isPresent();
      assertThat(result.get().userId()).isEqualTo("user-1");
      verify(userInfoCache).put(any(UserInfo.class));
    }

    @Test
    void soapFailure_returnsEmpty() throws Exception {
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
      when(mailboxClient.send(any())).thenThrow(new MailboxClientException("test"));

      Optional<UserInfo> result = userService.getUserById("user-1", "token-1");

      assertThat(result).isEmpty();
      verify(userInfoCache, never()).put(any());
    }

    @Test
    void cacheDisabled_soapResultNotCached() throws Exception {
      when(userInfoCache.isCacheEnabled()).thenReturn(false);
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
      when(mailboxClient.send(any())).thenThrow(new MailboxClientException("test"));

      Optional<UserInfo> result = userService.getUserById("user-1", "token-1");

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

      Optional<UserInfo> result = userService.getUserByEmail("user@example.com", "token-1");

      assertThat(result).contains(userInfo);
      verify(mailboxClient, never()).send(any());
    }

    @Test
    void cacheMiss_callsSoap() throws Exception {
      when(userInfoCache.getByEmail("user@example.com")).thenReturn(Optional.empty());
      when(mailboxClient.send(any())).thenThrow(new MailboxClientException("test"));

      Optional<UserInfo> result = userService.getUserByEmail("user@example.com", "token-1");

      assertThat(result).isEmpty();
      verify(mailboxClient).send(any());
    }

    @Test
    void soapSuccess_cachesAndReturnsUserInfo() throws Exception {
      when(userInfoCache.getByEmail("user@example.com")).thenReturn(Optional.empty());

      GetAccountInfoResponse response = mockAccountInfoResponse("user-1", "user@example.com");
      when(mailboxClient.send(any())).thenReturn(response);

      Optional<UserInfo> result = userService.getUserByEmail("user@example.com", "token-1");

      assertThat(result).isPresent();
      assertThat(result.get().email()).isEqualTo("user@example.com");
      verify(userInfoCache).put(any(UserInfo.class));
    }

    @Test
    void soapFailure_returnsEmpty() throws Exception {
      when(userInfoCache.getByEmail("user@example.com")).thenReturn(Optional.empty());
      when(mailboxClient.send(any())).thenThrow(new MailboxClientException("test"));

      Optional<UserInfo> result = userService.getUserByEmail("user@example.com", "token-1");

      assertThat(result).isEmpty();
      verify(userInfoCache, never()).put(any());
    }
  }

  @Nested
  class GetUsersTests {

    @Test
    void allCacheHitsSkipsSoap() {
      UserInfo u1 = sampleUserInfo();
      UserInfo u2 = new UserInfo("user-2", "u2@example.com", "Jane", "example.com", "ACTIVE", "INTERNAL");
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.of(u1));
      when(userInfoCache.getByUserId("user-2")).thenReturn(Optional.of(u2));

      List<UserInfo> result = userService.getUsers(List.of("user-1", "user-2"), "token-1");

      assertThat(result).containsExactly(u1, u2);
      verifyNoInteractions(mailboxClient);
    }

    @Test
    void cacheMiss_callsSoapForMissingIds() throws Exception {
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
      GetAccountInfoResponse response = mockAccountInfoResponse("user-1", "user@example.com");
      when(mailboxClient.send(any())).thenReturn(response);

      List<UserInfo> result = userService.getUsers(List.of("user-1"), "token-1");

      assertThat(result).hasSize(1);
      assertThat(result.get(0).userId()).isEqualTo("user-1");
    }

    @Test
    void deduplicatesUserIds() throws Exception {
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
      GetAccountInfoResponse response = mockAccountInfoResponse("user-1", "user@example.com");
      when(mailboxClient.send(any())).thenReturn(response);

      List<UserInfo> result = userService.getUsers(List.of("user-1", "user-1"), "token-1");

      assertThat(result).hasSize(1);
    }

    @Test
    void returnsOnlyFoundUsers() throws Exception {
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.of(sampleUserInfo()));
      when(userInfoCache.getByUserId("user-not-found")).thenReturn(Optional.empty());
      when(mailboxClient.send(any())).thenThrow(new MailboxClientException("not found"));

      List<UserInfo> result = userService.getUsers(List.of("user-1", "user-not-found"), "token-1");

      assertThat(result).hasSize(1);
      assertThat(result.get(0).userId()).isEqualTo("user-1");
    }

    @Test
    void emptyInputReturnsEmpty() {
      List<UserInfo> result = userService.getUsers(List.of(), "token-1");
      assertThat(result).isEmpty();
      verifyNoInteractions(mailboxClient);
    }
  }

  // -- helpers --

  private GetAccountInfoResponse mockAccountInfoResponse(String userId, String email) {
    GetAccountInfoResponse response = mock(GetAccountInfoResponse.class);
    NamedValue idAttr = mock(NamedValue.class);
    when(idAttr.getName()).thenReturn("zimbraId");
    when(idAttr.getValue()).thenReturn(userId);
    NamedValue nameAttr = mock(NamedValue.class);
    when(nameAttr.getName()).thenReturn("displayName");
    when(nameAttr.getValue()).thenReturn("Test User");
    when(response.getAttr()).thenReturn(List.of(idAttr, nameAttr));
    when(response.getName()).thenReturn(email);
    when(response.getPublicURL()).thenReturn("example.com");
    return response;
  }
}
