// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.cache;

import static com.zextras.carbonio.user_management.UserManagementServiceConfig.FeatureFlags;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Ticker;
import com.zextras.carbonio.quarkus.extensions.bootstrap.ApplicationConfigService;
import com.zextras.carbonio.user_management.cache.record.UserDetails;
import java.time.Clock;
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
    cache = new UserDetailsCache(configService, ticker, Clock.systemUTC());
  }

  private UserDetails sampleDetails() {
    return new UserDetails("en", Map.of(FeatureFlags.FILES_ENABLED, true));
  }

  @Test
  void putAndGetByToken() {
    cache.put("token-abc", "user-1", sampleDetails(), System.currentTimeMillis() + 30000);

    assertThat(cache.getByToken("token-abc")).contains(sampleDetails());
  }

  @Test
  void resolveUserId() {
    cache.put("token-abc", "user-1", sampleDetails(), System.currentTimeMillis() + 30000);

    assertThat(cache.resolveUserId("token-abc")).contains("user-1");
  }

  @Test
  void resolveUserIdReturnsEmptyWhenUnknown() {
    assertThat(cache.resolveUserId("unknown-token")).isEmpty();
  }

  @Test
  void getByTokenReturnsEmptyWhenUnknown() {
    assertThat(cache.getByToken("unknown-token")).isEmpty();
  }

  @Test
  void ttlUsesMinOfConfigAndRemaining() {
    // Config = 5 seconds, remaining = 30 seconds → should use 5s
    when(configService.get("cache.userdetails-ttl")).thenReturn(Optional.of("5"));
    UserDetailsCache configCache = new UserDetailsCache(configService, currentTime::get, Clock.systemUTC());

    configCache.put("token-abc", "user-1", sampleDetails(), System.currentTimeMillis() + 30000);

    currentTime.set(TimeUnit.SECONDS.toNanos(6));
    assertThat(configCache.getByToken("token-abc")).isEmpty();
  }

  @Test
  void ttlUsesRemainingWhenSmallerThanConfig() {
    // Config = 60 seconds, remaining = 5 seconds → should use 5s
    when(configService.get("cache.userdetails-ttl")).thenReturn(Optional.of("60"));
    UserDetailsCache configCache = new UserDetailsCache(configService, currentTime::get, Clock.systemUTC());

    configCache.put("token-abc", "user-1", sampleDetails(), System.currentTimeMillis() + 5000);

    currentTime.set(TimeUnit.SECONDS.toNanos(6));
    assertThat(configCache.getByToken("token-abc")).isEmpty();
  }

  @Test
  void entryAvailableBeforeExpiryAndGoneAfter() {
    // No config → uses remaining only (500ms)
    cache.put("token-abc", "user-1", sampleDetails(), System.currentTimeMillis() + 500);

    // 400ms → still in cache
    currentTime.set(TimeUnit.MILLISECONDS.toNanos(400));
    assertThat(cache.getByToken("token-abc")).contains(sampleDetails());

    // 1s → expired
    currentTime.set(TimeUnit.MILLISECONDS.toNanos(1000));
    assertThat(cache.getByToken("token-abc")).isEmpty();
  }

  @Test
  void invalidateByTokenRemovesEntry() {
    cache.put("token-abc", "user-1", sampleDetails(), System.currentTimeMillis() + 30000);

    cache.invalidateByToken("token-abc");

    assertThat(cache.getByToken("token-abc")).isEmpty();
  }

  @Test
  void multipleTokensForSameUserAreIndependentEntries() {
    long expiresAt = System.currentTimeMillis() + 30000;
    cache.put("token-A", "user-1", sampleDetails(), expiresAt);
    cache.put("token-B", "user-1", sampleDetails(), expiresAt);

    assertThat(cache.getByToken("token-A")).contains(sampleDetails());
    assertThat(cache.getByToken("token-B")).contains(sampleDetails());
    assertThat(cache.resolveUserId("token-A")).contains("user-1");
    assertThat(cache.resolveUserId("token-B")).contains("user-1");
  }

  @Test
  void invalidatingOneTokenDoesNotAffectOther() {
    long expiresAt = System.currentTimeMillis() + 30000;
    cache.put("token-A", "user-1", sampleDetails(), expiresAt);
    cache.put("token-B", "user-1", sampleDetails(), expiresAt);

    cache.invalidateByToken("token-A");

    assertThat(cache.getByToken("token-A")).isEmpty();
    assertThat(cache.getByToken("token-B")).contains(sampleDetails());
  }

  @Test
  void eachTokenHasIndependentTtl() {
    cache.put("token-short", "user-1", sampleDetails(), System.currentTimeMillis() + 500);
    cache.put("token-long", "user-1", sampleDetails(), System.currentTimeMillis() + 30000);

    // 1s → short expired, long still alive
    currentTime.set(TimeUnit.MILLISECONDS.toNanos(1000));
    assertThat(cache.getByToken("token-short")).isEmpty();
    assertThat(cache.getByToken("token-long")).contains(sampleDetails());
  }
}
