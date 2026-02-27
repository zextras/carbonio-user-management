// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Ticker;
import com.zextras.carbonio.quarkus.extensions.bootstrap.ApplicationConfigService;
import com.zextras.carbonio.user_management.cache.record.UserDetails;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserDetailsCacheTest {

  private ApplicationConfigService configService;
  private AtomicLong currentTime;
  private UserDetailsCache cache;

  @BeforeEach
  void setUp() {
    configService = mock(ApplicationConfigService.class);
    // No config → uses remaining token lifetime only
    when(configService.get("cache.userdetails-ttl")).thenReturn(Optional.empty());
    currentTime = new AtomicLong(0);
    Ticker ticker = currentTime::get;
    cache = new UserDetailsCache(configService, ticker);
  }

  private UserDetails sampleDetails() {
    return new UserDetails("en", Map.of("carbonioFeatureX", "true"));
  }

  @Test
  void putAndGetByUserId() {
    cache.put("user-1", "token-abc", sampleDetails(), 30000);

    assertThat(cache.getByUserId("user-1")).contains(sampleDetails());
  }

  @Test
  void putAndGetByToken() {
    cache.put("user-1", "token-abc", sampleDetails(), 30000);

    assertThat(cache.getByToken("token-abc")).contains(sampleDetails());
  }

  @Test
  void resolveUserId() {
    cache.put("user-1", "token-abc", sampleDetails(), 30000);

    assertThat(cache.resolveUserId("token-abc")).contains("user-1");
  }

  @Test
  void resolveUserIdReturnsEmptyWhenUnknown() {
    assertThat(cache.resolveUserId("unknown-token")).isEmpty();
  }

  @Test
  void entryExpiresAfterTokenLifetime() {
    cache.put("user-1", "token-abc", sampleDetails(), 10000);

    currentTime.set(TimeUnit.MILLISECONDS.toNanos(10001));

    assertThat(cache.getByUserId("user-1")).isEmpty();
  }

  @Test
  void ttlUsesMinOfConfigAndRemaining() {
    // Config = 5 seconds, remaining = 30 seconds → should use 5s
    when(configService.get("cache.userdetails-ttl")).thenReturn(Optional.of("5"));
    UserDetailsCache configCache = new UserDetailsCache(configService, currentTime::get);

    configCache.put("user-1", "token-abc", sampleDetails(), 30000);

    currentTime.set(TimeUnit.SECONDS.toNanos(6));
    assertThat(configCache.getByUserId("user-1")).isEmpty();
  }

  @Test
  void ttlUsesRemainingWhenSmallerThanConfig() {
    // Config = 60 seconds, remaining = 5 seconds → should use 5s
    when(configService.get("cache.userdetails-ttl")).thenReturn(Optional.of("60"));
    UserDetailsCache configCache = new UserDetailsCache(configService, currentTime::get);

    configCache.put("user-1", "token-abc", sampleDetails(), 5000);

    currentTime.set(TimeUnit.SECONDS.toNanos(6));
    assertThat(configCache.getByUserId("user-1")).isEmpty();
  }

  @Test
  void usesOnlyRemainingWhenConfigAbsent() {
    // No config set (setUp default) → uses remaining only
    cache.put("user-1", "token-abc", sampleDetails(), 20000);

    // Still present at 19s
    currentTime.set(TimeUnit.SECONDS.toNanos(19));
    assertThat(cache.getByUserId("user-1")).isPresent();

    // Expired at 21s
    currentTime.set(TimeUnit.SECONDS.toNanos(21));
    assertThat(cache.getByUserId("user-1")).isEmpty();
  }

  @Test
  void removalListenerCleansTokenIndex() {
    cache.put("user-1", "token-abc", sampleDetails(), 30000);

    cache.invalidate("user-1");
    cache.getByUserId("user-1");

    assertThat(cache.getByToken("token-abc")).isEmpty();
  }
}
