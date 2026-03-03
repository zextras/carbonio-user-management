// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Ticker;
import com.zextras.carbonio.quarkus.extensions.bootstrap.ApplicationConfigService;
import com.zextras.carbonio.user_management.record.UserInfo;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserInfoCacheTest {

  private ApplicationConfigService configService;
  private AtomicLong currentTime;
  private UserInfoCache cache;

  @BeforeEach
  void setUp() {
    configService = mock(ApplicationConfigService.class);
    when(configService.get("cache.userinfo-ttl")).thenReturn(Optional.of("60"));
    currentTime = new AtomicLong(0);
    Ticker ticker = currentTime::get;
    cache = new UserInfoCache(configService, ticker, Clock.systemUTC());
  }

  private UserInfo sampleUser(String id, String email) {
    return new UserInfo(id, email, "Full Name", "example.com", "ACTIVE", "INTERNAL");
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

    // 50s → still in cache (both access paths)
    currentTime.set(TimeUnit.SECONDS.toNanos(50));
    assertThat(cache.getByUserId("user-1")).contains(user);
    assertThat(cache.getByEmail("user@example.com")).contains(user);

    // 61s → expired (both access paths)
    currentTime.set(TimeUnit.SECONDS.toNanos(61));
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
  void updateResetsTtl() {
    UserInfo user1 = sampleUser("user-1", "user@example.com");
    cache.put(user1);

    currentTime.set(TimeUnit.SECONDS.toNanos(50));

    UserInfo user1Updated = new UserInfo(
        "user-1", "user@example.com", "Updated Name", "example.com", "ACTIVE", "INTERNAL");
    cache.put(user1Updated);

    // 61s from original put, but only 11s from re-put → still alive (TTL reset to 60s)
    currentTime.set(TimeUnit.SECONDS.toNanos(61));
    assertThat(cache.getByUserId("user-1")).contains(user1Updated);

    // 111s from original put, 61s from re-put → expired
    currentTime.set(TimeUnit.SECONDS.toNanos(111));
    assertThat(cache.getByUserId("user-1")).isEmpty();
  }

  @Test
  void throwsWhenConfigMissingOnPut() {
    when(configService.get("cache.userinfo-ttl")).thenReturn(Optional.empty());
    UserInfoCache cacheNoConfig = new UserInfoCache(configService, currentTime::get, Clock.systemUTC());

    assertThatThrownBy(() -> cacheNoConfig.put(sampleUser("user-1", "u@x.com")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("cache.userinfo-ttl");
  }
}
