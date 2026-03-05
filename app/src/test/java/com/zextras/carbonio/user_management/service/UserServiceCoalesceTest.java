// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zextras.carbonio.user_management.cache.UserInfoCache;
import com.zextras.carbonio.user_management.cache.UserMyselfCache;
import com.zextras.carbonio.user_management.record.UserInfo;
import com.zextras.carbonio.user_management.repository.UserInfoCacheRepository;
import com.zextras.carbonio.user_management.repository.UserMyselfCacheRepository;
import com.zextras.carbonio.user_management.service.UserService;
import com.zextras.mailbox.client.service.ServiceClient;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import zimbra.NamedValue;
import zimbraaccount.GetAccountInfoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the request coalescing behavior in UserService.
 * Concurrent calls for the same key should result in a single SOAP call.
 */
class UserServiceCoalesceTest {

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
        mailboxClient, userInfoCache, userMyselfCache, userInfoCacheRepo, userMyselfCacheRepo);
  }

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

  @Test
  void concurrentGetUserByIdCallsCoalesceIntoSingleSoapCall() throws Exception {
    // L2 miss
    when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
    // L1 miss
    when(userInfoCacheRepo.findByUserId("user-1")).thenReturn(Optional.empty());
    // Cache write setup
    when(userInfoCache.computeExpiresAt()).thenReturn(System.currentTimeMillis() + 60_000);
    when(userInfoCacheRepo.upsert(any(), anyLong())).thenAnswer(inv -> inv.getArgument(0));

    CountDownLatch soapStarted = new CountDownLatch(1);
    CountDownLatch soapProceed = new CountDownLatch(1);
    AtomicInteger soapCallCount = new AtomicInteger(0);

    GetAccountInfoResponse response = mockAccountInfoResponse("user-1", "u@x.com");
    when(mailboxClient.send(any())).thenAnswer(inv -> {
      soapCallCount.incrementAndGet();
      soapStarted.countDown();
      soapProceed.await(10, TimeUnit.SECONDS);
      return response;
    });

    // Launch first call
    CompletableFuture<Optional<UserInfo>> call1 = CompletableFuture.supplyAsync(
        () -> userService.getUserById("user-1", "token-1"));

    // Wait for SOAP to be entered by first call
    assertThat(soapStarted.await(10, TimeUnit.SECONDS)).isTrue();

    // Launch second call — should coalesce with the first
    CompletableFuture<Optional<UserInfo>> call2 = CompletableFuture.supplyAsync(
        () -> userService.getUserById("user-1", "token-1"));

    // Give call2 time to reach coalesce and find existing inflight future
    Thread.sleep(200);

    // Release SOAP
    soapProceed.countDown();

    // Both calls should complete with a result
    Optional<UserInfo> result1 = call1.get(10, TimeUnit.SECONDS);
    Optional<UserInfo> result2 = call2.get(10, TimeUnit.SECONDS);

    assertThat(result1).isPresent();
    assertThat(result2).isPresent();
    assertThat(result1.get().userId()).isEqualTo("user-1");
    assertThat(result2.get().userId()).isEqualTo("user-1");

    // SOAP should have been called exactly once
    assertThat(soapCallCount.get()).isEqualTo(1);
  }

  @Test
  void differentUserIdsDontCoalesce() throws Exception {
    // L2 miss for both
    when(userInfoCache.getByUserId("user-1")).thenReturn(Optional.empty());
    when(userInfoCache.getByUserId("user-2")).thenReturn(Optional.empty());
    // L1 miss for both
    when(userInfoCacheRepo.findByUserId("user-1")).thenReturn(Optional.empty());
    when(userInfoCacheRepo.findByUserId("user-2")).thenReturn(Optional.empty());
    // Cache setup
    when(userInfoCache.computeExpiresAt()).thenReturn(System.currentTimeMillis() + 60_000);
    when(userInfoCacheRepo.upsert(any(), anyLong())).thenAnswer(inv -> inv.getArgument(0));

    GetAccountInfoResponse response1 = mockAccountInfoResponse("user-1", "u1@x.com");
    GetAccountInfoResponse response2 = mockAccountInfoResponse("user-2", "u2@x.com");

    AtomicInteger callCount = new AtomicInteger(0);
    when(mailboxClient.send(any())).thenAnswer(inv -> {
      int n = callCount.incrementAndGet();
      return n == 1 ? response1 : response2;
    });

    // Launch both calls in parallel
    CompletableFuture<Optional<UserInfo>> call1 = CompletableFuture.supplyAsync(
        () -> userService.getUserById("user-1", "token-1"));
    CompletableFuture<Optional<UserInfo>> call2 = CompletableFuture.supplyAsync(
        () -> userService.getUserById("user-2", "token-1"));

    Optional<UserInfo> result1 = call1.get(10, TimeUnit.SECONDS);
    Optional<UserInfo> result2 = call2.get(10, TimeUnit.SECONDS);

    assertThat(result1).isPresent();
    assertThat(result2).isPresent();

    // Both should have made independent SOAP calls
    verify(mailboxClient, times(2)).send(any());
  }
}
