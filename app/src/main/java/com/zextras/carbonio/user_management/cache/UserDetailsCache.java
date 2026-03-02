// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Ticker;
import com.zextras.carbonio.quarkus.extensions.bootstrap.ApplicationConfigService;
import com.zextras.carbonio.user_management.UserManagementServiceConfig.ApplicationConfig;
import com.zextras.carbonio.user_management.cache.record.UserDetails;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache for {@link UserDetails}, keyed by auth token.
 *
 * <p>Each entry's TTL is computed at insertion time as the minimum of the config value
 * ({@code cache.userdetails-ttl}) and the remaining session lifetime from mailbox. If config
 * is absent, the session remaining time is used directly. Config is read at every insertion via
 * Caffeine's {@code VarExpiration} API, so changes on Consul take effect immediately.
 *
 * <p>A lightweight {@code tokenToUserId} map provides token-to-userId resolution, kept in sync
 * via Caffeine's {@code removalListener}: when an entry expires, the listener removes the
 * corresponding mapping in O(1).
 */
@Singleton
public class UserDetailsCache {

  private final Cache<String, UserDetails> cache;
  private final ConcurrentHashMap<String, String> tokenToUserId;
  private final ApplicationConfigService configService;
  private final Clock clock;

  @Inject
  public UserDetailsCache(ApplicationConfigService configService) {
    this(configService, Ticker.systemTicker(), Clock.systemUTC());
  }

  UserDetailsCache(ApplicationConfigService configService, Ticker ticker, Clock clock) {
    this.configService = configService;
    this.clock = clock;
    this.tokenToUserId = new ConcurrentHashMap<>();

    this.cache = Caffeine.newBuilder()
        .ticker(ticker)
        .expireAfter(
            Expiry.<String, UserDetails>creating((k, v) -> Duration.ofNanos(Long.MAX_VALUE)))
        .removalListener((key, value, cause) -> tokenToUserId.remove(key))
        .build();
  }

  public Optional<UserDetails> getByToken(String token) {
    return Optional.ofNullable(cache.getIfPresent(token));
  }

  public Optional<String> resolveUserId(String token) {
    return Optional.ofNullable(tokenToUserId.get(token));
  }

  public void put(String token, String userId, UserDetails details, long expiresAt) {
    long remainingMs = Math.max(0, expiresAt - clock.millis());
    Duration ttl = capTtl(remainingMs);
    cache.policy().expireVariably().orElseThrow().put(token, details, ttl);
    tokenToUserId.put(token, userId);
  }

  public void invalidateByToken(String token) {
    cache.invalidate(token);
  }

  void clearAll() {
    cache.invalidateAll();
    tokenToUserId.clear();
  }

  /**
   * Returns an epoch-millis expiration timestamp for a new entry, computed as
   * {@code now + min(configTtl, sessionRemainingMs)}. Used by the service layer to persist
   * entries in the shared DB and pass as {@code expiresAt} to {@link #put}.
   */
  public long computeExpiresAt(long sessionRemainingMs) {
    return clock.millis() + capTtl(sessionRemainingMs).toMillis();
  }

  private Duration capTtl(long remainingMs) {
    Duration remaining = Duration.ofMillis(remainingMs);
    return configService.get(ApplicationConfig.CACHE_DETAILS_TTL)
        .map(Long::parseLong)
        .map(seconds -> min(Duration.ofSeconds(seconds), remaining))
        .orElse(remaining);
  }

  private static Duration min(Duration a, Duration b) {
    return a.compareTo(b) < 0 ? a : b;
  }
}
