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
import com.zextras.carbonio.user_management.cache.UserMyselfCache;
import com.zextras.carbonio.user_management.record.UserMyself;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserMyselfCacheTest {

  private ApplicationConfigService configService;
  private AtomicLong currentTime;
  private UserMyselfCache cache;

  @BeforeEach
  void setUp() {
    configService = mock(ApplicationConfigService.class);
    when(configService.get("cache.usermyself-ttl")).thenReturn(Optional.empty());
    currentTime = new AtomicLong(0);
    Ticker ticker = currentTime::get;
    cache = new UserMyselfCache(configService, ticker, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
  }

  private UserMyself sampleMyself() {
    return new UserMyself(
        "user-1", "user@example.com", "John Doe", "example.com",
        "ACTIVE", "INTERNAL", "en", List.of("carbonioFeatureFilesEnabled"), Map.of());
  }

  @Test
  void isCacheEnabled_returnsTrueWhenTtlPositive() {
    when(configService.get("cache.usermyself-ttl")).thenReturn(Optional.of("60"));

    assertThat(cache.isCacheEnabled()).isTrue();
  }

  @Test
  void isCacheEnabled_returnsFalseWhenTtlZero() {
    when(configService.get("cache.usermyself-ttl")).thenReturn(Optional.of("0"));

    assertThat(cache.isCacheEnabled()).isFalse();
  }

  @Test
  void isCacheEnabled_returnsTrueWhenConfigAbsent() {
    // setUp already sets config to Optional.empty()
    assertThat(cache.isCacheEnabled()).isTrue();
  }

  @Test
  void putAndGetByToken() {
    cache.put("token-abc", "user-1", sampleMyself(), 30_000L);

    assertThat(cache.getByToken("token-abc")).contains(sampleMyself());
  }

  @Test
  void resolveUserId() {
    cache.put("token-abc", "user-1", sampleMyself(), 30_000L);

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
    // Config = 5 seconds, remaining = 30 seconds -> should use 5s
    when(configService.get("cache.usermyself-ttl")).thenReturn(Optional.of("5"));
    UserMyselfCache configCache = new UserMyselfCache(configService, currentTime::get, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

    configCache.put("token-abc", "user-1", sampleMyself(), 30_000L);

    currentTime.set(TimeUnit.SECONDS.toNanos(6));
    assertThat(configCache.getByToken("token-abc")).isEmpty();
  }

  @Test
  void ttlUsesRemainingWhenSmallerThanConfig() {
    // Config = 60 seconds, remaining = 5 seconds -> should use 5s
    when(configService.get("cache.usermyself-ttl")).thenReturn(Optional.of("60"));
    UserMyselfCache configCache = new UserMyselfCache(configService, currentTime::get, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

    configCache.put("token-abc", "user-1", sampleMyself(), 5_000L);

    currentTime.set(TimeUnit.SECONDS.toNanos(6));
    assertThat(configCache.getByToken("token-abc")).isEmpty();
  }

  @Test
  void entryAvailableBeforeExpiryAndGoneAfter() {
    cache.put("token-abc", "user-1", sampleMyself(), 500L);

    // 400ms -> still in cache
    currentTime.set(TimeUnit.MILLISECONDS.toNanos(400));
    assertThat(cache.getByToken("token-abc")).contains(sampleMyself());

    // 1s -> expired
    currentTime.set(TimeUnit.MILLISECONDS.toNanos(1000));
    assertThat(cache.getByToken("token-abc")).isEmpty();
  }

  @Test
  void invalidateByTokenRemovesEntry() {
    cache.put("token-abc", "user-1", sampleMyself(), 30_000L);

    cache.invalidateByToken("token-abc");

    assertThat(cache.getByToken("token-abc")).isEmpty();
  }

  @Test
  void oneTokenPerUser_newTokenInvalidatesOldToken() {
    long expiresAt = 30_000L;
    cache.put("token-A", "user-1", sampleMyself(), expiresAt);
    cache.put("token-B", "user-1", sampleMyself(), expiresAt);

    // Old token should be invalidated
    assertThat(cache.getByToken("token-A")).isEmpty();
    // New token should be present
    assertThat(cache.getByToken("token-B")).contains(sampleMyself());
    assertThat(cache.resolveUserId("token-B")).contains("user-1");
  }

  @Test
  void oneTokenPerUser_sameTokenReplaceIsSafe() {
    long expiresAt = 30_000L;
    cache.put("token-A", "user-1", sampleMyself(), expiresAt);
    // Re-put the same token (e.g., refresh from L1)
    cache.put("token-A", "user-1", sampleMyself(), expiresAt);

    assertThat(cache.getByToken("token-A")).contains(sampleMyself());
    assertThat(cache.resolveUserId("token-A")).contains("user-1");
  }

  @Test
  void differentUsersHaveIndependentTokens() {
    long expiresAt = 30_000L;
    UserMyself myself1 = sampleMyself();
    UserMyself myself2 = new UserMyself(
        "user-2", "other@example.com", "Jane Doe", "example.com",
        "ACTIVE", "INTERNAL", "it", List.of(), Map.of());

    cache.put("token-A", "user-1", myself1, expiresAt);
    cache.put("token-B", "user-2", myself2, expiresAt);

    assertThat(cache.getByToken("token-A")).contains(myself1);
    assertThat(cache.getByToken("token-B")).contains(myself2);
  }

  @Test
  void ttlConfigIsCachedFor60sAndRefreshedAfter() {
    when(configService.get("cache.usermyself-ttl")).thenReturn(Optional.of("60"));

    // First read → fetches from consul
    cache.isCacheEnabled();
    verify(configService, times(1)).get("cache.usermyself-ttl");

    // Second read within 60s → served from local cache, no consul call
    currentTime.set(TimeUnit.SECONDS.toNanos(30));
    cache.isCacheEnabled();
    verify(configService, times(1)).get("cache.usermyself-ttl");

    // After 61s → config cache expired, re-fetches from consul
    currentTime.set(TimeUnit.SECONDS.toNanos(61));
    when(configService.get("cache.usermyself-ttl")).thenReturn(Optional.of("0"));
    boolean enabled = cache.isCacheEnabled();
    verify(configService, times(2)).get("cache.usermyself-ttl");
    assertThat(enabled).isFalse();
  }
}
