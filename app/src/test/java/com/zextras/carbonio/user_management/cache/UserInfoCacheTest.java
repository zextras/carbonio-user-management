// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Ticker;
import com.zextras.carbonio.quarkus.extensions.bootstrap.ApplicationConfigService;
import com.zextras.carbonio.user_management.record.UserInfo;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserInfoCacheTest {

  private static final long DEFAULT_TTL_SECONDS = 900;

  private ApplicationConfigService configService;
  private AtomicLong currentTime;
  private UserInfoCache cache;

  @BeforeEach
  void setUp() {
    configService = mock(ApplicationConfigService.class);
    when(configService.get("cache.userinfo-ttl")).thenReturn(Optional.empty());
    currentTime = new AtomicLong(0);
    Ticker ticker = currentTime::get;
    cache = new UserInfoCache(configService, ticker);
  }

  private UserInfo sampleUser(String id, String email) {
    return new UserInfo(
        id, email, "Full Name", "example.com", "ACTIVE", "INTERNAL", "cos-1", "dom-1", false);
  }

  @Test
  void putAndGetByUserId() {
    UserInfo user = sampleUser("user-1", "user@example.com");
    cache.put(user);

    assertThat(cache.getByUserId("user-1")).contains(user);
  }

  @Test
  void getByUserIdReturnsEmptyWhenNotPresent() {
    assertThat(cache.getByUserId("nonexistent")).isEmpty();
  }

  @Test
  void putAndGetByEmail() {
    UserInfo user = sampleUser("user-1", "user@example.com");
    cache.put(user);

    assertThat(cache.getByEmail("user@example.com")).contains(user);
  }

  @Test
  void getByEmailReturnsEmptyWhenNotPresent() {
    assertThat(cache.getByEmail("notfound@example.com")).isEmpty();
  }

  @Test
  void entryAvailableBeforeExpiryAndGoneAfter() {
    UserInfo user = sampleUser("user-1", "user@example.com");
    cache.put(user);

    // TTL - 100s → still in cache (both access paths)
    currentTime.set(TimeUnit.SECONDS.toNanos(DEFAULT_TTL_SECONDS - 100));
    assertThat(cache.getByUserId("user-1")).contains(user);
    assertThat(cache.getByEmail("user@example.com")).contains(user);

    // TTL + 1s → expired (both access paths)
    currentTime.set(TimeUnit.SECONDS.toNanos(DEFAULT_TTL_SECONDS + 1));
    assertThat(cache.getByUserId("user-1")).isEmpty();
    assertThat(cache.getByEmail("user@example.com")).isEmpty();
  }

  @Test
  void removalListenerCleansEmailIndex() {
    UserInfo user = sampleUser("user-1", "user@example.com");
    cache.put(user);

    cache.invalidate("user-1");
    cache.getByUserId("user-1");

    assertThat(cache.getByEmail("user@example.com")).isEmpty();
  }

  @Test
  void emailIndexSurvivesAReputAfterExpiry() {
    UserInfo user = sampleUser("user-1", "user@example.com");
    cache.put(user);
    currentTime.set(TimeUnit.SECONDS.toNanos(DEFAULT_TTL_SECONDS + 1));
    assertThat(cache.getByUserId("user-1")).isEmpty();

    cache.put(user);
    ForkJoinPool.commonPool().awaitQuiescence(5, TimeUnit.SECONDS);

    assertThat(cache.getByEmail("user@example.com")).contains(user);
  }

  @Test
  void holdsAtMostTheConfiguredNumberOfEntries() {
    UserInfoCache small = new UserInfoCache(configService, currentTime::get, 2);
    for (int i = 0; i < 10; i++) {
      small.put(sampleUser("user-" + i, "user" + i + "@example.com"));
    }

    long held = 0;
    for (int i = 0; i < 10; i++) {
      boolean byId = small.getByUserId("user-" + i).isPresent();
      assertThat(small.getByEmail("user" + i + "@example.com").isPresent()).isEqualTo(byId);
      held += byId ? 1 : 0;
    }
    assertThat(held).isLessThanOrEqualTo(2);
  }

  @Test
  void renamedUserIsNotFoundByTheOldEmail() {
    cache.put(sampleUser("user-1", "old@example.com"));
    cache.put(sampleUser("user-1", "new@example.com"));

    assertThat(cache.getByEmail("old@example.com")).isEmpty();
    assertThat(cache.getByEmail("new@example.com")).isPresent();
  }

  @Test
  void updateResetsTtl() {
    UserInfo user1 = sampleUser("user-1", "user@example.com");
    cache.put(user1);

    currentTime.set(TimeUnit.SECONDS.toNanos(DEFAULT_TTL_SECONDS - 100));

    UserInfo user1Updated =
        new UserInfo(
            "user-1",
            "user@example.com",
            "Updated Name",
            "example.com",
            "ACTIVE",
            "INTERNAL",
            "cos-1",
            "dom-1",
            false);
    cache.put(user1Updated);

    // TTL + 1s from original put, but only 101s from re-put → still alive
    currentTime.set(TimeUnit.SECONDS.toNanos(DEFAULT_TTL_SECONDS + 1));
    assertThat(cache.getByUserId("user-1")).contains(user1Updated);

    // 2 TTL + 1s from original put, TTL + 101s from re-put → expired
    currentTime.set(TimeUnit.SECONDS.toNanos(2 * DEFAULT_TTL_SECONDS + 1));
    assertThat(cache.getByUserId("user-1")).isEmpty();
  }

  @Test
  void isCacheEnabled_returnsTrueWhenConfigAbsent() {
    assertThat(cache.isCacheEnabled()).isTrue();
  }

  @Test
  void isCacheEnabled_returnsFalseWhenTtlZero() {
    when(configService.get("cache.userinfo-ttl")).thenReturn(Optional.of("0"));
    UserInfoCache zeroCache = new UserInfoCache(configService, currentTime::get);

    assertThat(zeroCache.isCacheEnabled()).isFalse();
  }

  @Test
  void customTtlOverridesDefault() {
    long customTtlSeconds = 21600; // 6 hours
    when(configService.get("cache.userinfo-ttl")).thenReturn(Optional.of("21600"));
    UserInfoCache customCache = new UserInfoCache(configService, currentTime::get);

    customCache.put(sampleUser("user-1", "u@x.com"));

    // Just before 6h → still in cache
    currentTime.set(TimeUnit.SECONDS.toNanos(customTtlSeconds - 1));
    assertThat(customCache.getByUserId("user-1")).isPresent();

    // Just after 6h → expired
    currentTime.set(TimeUnit.SECONDS.toNanos(customTtlSeconds + 1));
    assertThat(customCache.getByUserId("user-1")).isEmpty();
  }

  @Test
  void ttlConfigIsCachedFor60sAndRefreshedAfter() {
    when(configService.get("cache.userinfo-ttl")).thenReturn(Optional.of("3600"));

    // First read → fetches from consul
    cache.readTtlSeconds();
    verify(configService, times(1)).get("cache.userinfo-ttl");

    // Second read within 60s → served from local cache, no consul call
    currentTime.set(TimeUnit.SECONDS.toNanos(30));
    cache.readTtlSeconds();
    verify(configService, times(1)).get("cache.userinfo-ttl");

    // After 61s → config cache expired, re-fetches from consul
    currentTime.set(TimeUnit.SECONDS.toNanos(61));
    when(configService.get("cache.userinfo-ttl")).thenReturn(Optional.of("7200"));
    long newTtl = cache.readTtlSeconds();
    verify(configService, times(2)).get("cache.userinfo-ttl");
    assertThat(newTtl).isEqualTo(7200);
  }
}
