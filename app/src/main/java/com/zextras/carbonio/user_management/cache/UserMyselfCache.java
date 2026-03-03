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
import com.zextras.carbonio.user_management.record.UserMyself;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache for {@link UserMyself}, keyed by auth token (one entry per user).
 *
 * <p>Each entry's TTL is computed at insertion time as the minimum of the config value
 * ({@code cache.usermyself-ttl}) and the remaining session lifetime from mailbox. If config
 * is absent, the session remaining time is used directly.
 *
 * <p>A reverse index {@code userIdToToken} enforces one-token-per-user: when a new token is
 * inserted for a userId that already has a cached token, the old entry is invalidated first.
 * The forward index {@code tokenToUserId} provides token-to-userId resolution.
 *
 * <p>Both indexes are kept in sync via Caffeine's {@code removalListener}.
 */
@Singleton
public class UserMyselfCache {

  private final Cache<String, UserMyself> cache;
  private final ConcurrentHashMap<String, String> tokenToUserId;
  private final ConcurrentHashMap<String, String> userIdToToken;
  private final ApplicationConfigService configService;
  private final Clock clock;

  @Inject
  public UserMyselfCache(ApplicationConfigService configService) {
    this(configService, Ticker.systemTicker(), Clock.systemUTC());
  }

  UserMyselfCache(ApplicationConfigService configService, Ticker ticker, Clock clock) {
    this.configService = configService;
    this.clock = clock;
    this.tokenToUserId = new ConcurrentHashMap<>();
    this.userIdToToken = new ConcurrentHashMap<>();

    this.cache = Caffeine.newBuilder()
        .ticker(ticker)
        .expireAfter(
            Expiry.<String, UserMyself>creating((k, v) -> Duration.ofNanos(Long.MAX_VALUE)))
        .removalListener((String key, UserMyself value, com.github.benmanes.caffeine.cache.RemovalCause cause) -> {
          if (key != null) {
            String userId = tokenToUserId.remove(key);
            if (userId != null) {
              userIdToToken.remove(userId, key);
            }
          }
        })
        .build();
  }

  public Optional<UserMyself> getByToken(String token) {
    return Optional.ofNullable(cache.getIfPresent(token));
  }

  public Optional<String> resolveUserId(String token) {
    return Optional.ofNullable(tokenToUserId.get(token));
  }

  public void put(String token, String userId, UserMyself myself, long expiresAt) {
    // One-token-per-user: invalidate old token for this user if different
    String oldToken = userIdToToken.get(userId);
    if (oldToken != null && !oldToken.equals(token)) {
      cache.invalidate(oldToken);
    }

    long remainingMs = Math.max(0, expiresAt - clock.millis());
    Duration ttl = capTtl(remainingMs);
    cache.policy().expireVariably().orElseThrow().put(token, myself, ttl);
    tokenToUserId.put(token, userId);
    userIdToToken.put(userId, token);
  }

  public void invalidateByToken(String token) {
    cache.invalidate(token);
  }

  void clearAll() {
    cache.invalidateAll();
    tokenToUserId.clear();
    userIdToToken.clear();
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
    return configService.get(ApplicationConfig.CACHE_USERMYSELF_TTL)
        .map(Long::parseLong)
        .map(seconds -> min(Duration.ofSeconds(seconds), remaining))
        .orElse(remaining);
  }

  private static Duration min(Duration a, Duration b) {
    return a.compareTo(b) < 0 ? a : b;
  }
}
