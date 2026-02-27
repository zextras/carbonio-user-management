// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zextras.carbonio.user_management.cache.UserDetailsCache;
import com.zextras.carbonio.user_management.cache.UserInfoCache;
import com.zextras.carbonio.user_management.cache.record.UserDetails;
import com.zextras.carbonio.user_management.cache.record.UserInfo;
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
  private UserService userService;

  @BeforeEach
  void setUp() {
    mailboxClient = mock(ServiceClient.class);
    userInfoCache = mock(UserInfoCache.class);
    userDetailsCache = mock(UserDetailsCache.class);
    userService = new UserService(mailboxClient, userInfoCache, userDetailsCache);
  }

  private UserInfo sampleUserInfo() {
    return new UserInfo("user-1", "user@example.com", "John Doe", "example.com", "ACTIVE", "INTERNAL");
  }

  private UserDetails sampleDetails() {
    return new UserDetails("en", Map.of("carbonioFeatureX", "true"));
  }

  @Nested
  class GetUserMyselfTests {

    @Test
    void returnsCachedResultWhenBothCachesHit() {
      UserInfo info = sampleUserInfo();
      UserDetails details = sampleDetails();

      when(userDetailsCache.resolveUserId("token-1")).thenReturn(Optional.of("user-1"));
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.of(info));
      when(userDetailsCache.getByUserId("user-1")).thenReturn(Optional.of(details));

      Optional<MyselfResult> result = userService.getUserMyself("token-1");

      assertThat(result).isPresent();
      assertThat(result.get().info()).isEqualTo(info);
      assertThat(result.get().details()).isEqualTo(details);
      verify(mailboxClient, never()).send(any());
    }

    @Test
    void callsMailboxWhenTokenNotInCache() throws Exception {
      when(userDetailsCache.resolveUserId("token-1")).thenReturn(Optional.empty());
      when(mailboxClient.send(any())).thenThrow(new MailboxClientException("test"));

      Optional<MyselfResult> result = userService.getUserMyself("token-1");

      assertThat(result).isEmpty();
    }

    @Test
    void callsMailboxWhenPartialCacheMiss() throws Exception {
      UserInfo info = sampleUserInfo();

      when(userDetailsCache.resolveUserId("token-1")).thenReturn(Optional.of("user-1"));
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.of(info));
      when(userDetailsCache.getByUserId("user-1")).thenReturn(Optional.empty());
      when(mailboxClient.send(any())).thenThrow(new MailboxClientException("test"));

      Optional<MyselfResult> result = userService.getUserMyself("token-1");

      assertThat(result).isEmpty();
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
      verify(mailboxClient, never()).send(any());
    }

    @Test
    void returnsEmptyOnMailboxError() throws Exception {
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
      when(mailboxClient.send(any())).thenThrow(new MailboxClientException("test"));

      Optional<UserInfo> result = userService.getUserById("user-1", "token-1");

      assertThat(result).isEmpty();
    }

    @Test
    void cacheHitIgnoresTokenCompletely() {
      UserInfo info = sampleUserInfo();
      when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.of(info));

      Optional<UserInfo> result = userService.getUserById("user-1", "fake-or-empty-token");

      assertThat(result).contains(info);
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
      verify(mailboxClient, never()).send(any());
    }

    @Test
    void returnsEmptyOnMailboxError() throws Exception {
      when(userInfoCache.getByEmail("user@example.com")).thenReturn(Optional.empty());
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
    void returnsCachedUsersAndFiltersNulls() {
      UserInfo user1 = new UserInfo("id-1", "a@x.com", "A", "x.com", "ACTIVE", "INTERNAL");
      UserInfo user2 = new UserInfo("id-2", "b@x.com", "B", "x.com", "ACTIVE", "INTERNAL");

      when(userInfoCache.getByUserId("id-1")).thenReturn(Optional.of(user1));
      when(userInfoCache.getByUserId("id-2")).thenReturn(Optional.of(user2));
      when(userInfoCache.getByUserId("id-3")).thenReturn(Optional.empty());
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
  }
}
