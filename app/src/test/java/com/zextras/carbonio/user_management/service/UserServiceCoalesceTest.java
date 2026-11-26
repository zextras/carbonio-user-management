// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zextras.carbonio.user_management.cache.UserInfoCache;
import com.zextras.carbonio.user_management.cache.UserMyselfCache;
import com.zextras.carbonio.user_management.record.UserInfo;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the request coalescing behavior in UserService. Concurrent calls for the same key
 * should result in a single directory call.
 */
class UserServiceCoalesceTest {

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
    userService = new UserService(directory, userInfoCache, userMyselfCache);
  }

  private UserInfo userInfo(String userId, String email) {
    return new UserInfo(
        userId, email, "Test User", "example.com", "ACTIVE", "INTERNAL", "cos-1", "dom-1", false);
  }

  @Test
  void concurrentGetUserByIdCallsCoalesceIntoSingleDirectoryCall() throws Exception {
    // Cache miss
    when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());

    CountDownLatch apiStarted = new CountDownLatch(1);
    CountDownLatch apiProceed = new CountDownLatch(1);
    AtomicInteger apiCallCount = new AtomicInteger(0);

    UserInfo response = userInfo("user-1", "u@x.com");
    when(directory.byId("user-1"))
        .thenAnswer(
            inv -> {
              apiCallCount.incrementAndGet();
              apiStarted.countDown();
              apiProceed.await(10, TimeUnit.SECONDS);
              return Optional.of(response);
            });

    // Launch first call
    CompletableFuture<Optional<UserInfo>> call1 =
        CompletableFuture.supplyAsync(() -> userService.getUserById("user-1"));

    // Wait for the directory to be entered by first call
    assertThat(apiStarted.await(10, TimeUnit.SECONDS)).isTrue();

    // Launch second call — should coalesce with the first
    CompletableFuture<Optional<UserInfo>> call2 =
        CompletableFuture.supplyAsync(() -> userService.getUserById("user-1"));

    // Give call2 time to reach coalesce and find existing inflight future
    Thread.sleep(200);

    // Release the directory
    apiProceed.countDown();

    // Both calls should complete with a result
    Optional<UserInfo> result1 = call1.get(10, TimeUnit.SECONDS);
    Optional<UserInfo> result2 = call2.get(10, TimeUnit.SECONDS);

    assertThat(result1).isPresent();
    assertThat(result2).isPresent();
    assertThat(result1.get().userId()).isEqualTo("user-1");
    assertThat(result2.get().userId()).isEqualTo("user-1");

    // Directory should have been called exactly once
    assertThat(apiCallCount.get()).isEqualTo(1);
  }

  @Test
  void differentUserIdsDontCoalesce() throws Exception {
    // Cache miss for both
    when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
    when(userInfoCache.getByUserId("user-2")).thenReturn(Optional.empty());

    UserInfo response1 = userInfo("user-1", "u1@x.com");
    UserInfo response2 = userInfo("user-2", "u2@x.com");

    AtomicInteger callCount = new AtomicInteger(0);
    when(directory.byId("user-1")).thenReturn(Optional.of(response1));
    when(directory.byId("user-2")).thenReturn(Optional.of(response2));

    // Launch both calls in parallel
    CompletableFuture<Optional<UserInfo>> call1 =
        CompletableFuture.supplyAsync(() -> userService.getUserById("user-1"));
    CompletableFuture<Optional<UserInfo>> call2 =
        CompletableFuture.supplyAsync(() -> userService.getUserById("user-2"));

    Optional<UserInfo> result1 = call1.get(10, TimeUnit.SECONDS);
    Optional<UserInfo> result2 = call2.get(10, TimeUnit.SECONDS);

    assertThat(result1).isPresent();
    assertThat(result2).isPresent();

    // Both should have made independent directory calls
    verify(directory, times(1)).byId("user-1");
    verify(directory, times(1)).byId("user-2");
  }
}
