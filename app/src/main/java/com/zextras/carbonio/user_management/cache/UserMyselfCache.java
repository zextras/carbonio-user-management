// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.Ticker;
import com.zextras.carbonio.quarkus.extensions.bootstrap.ApplicationConfigService;
import com.zextras.carbonio.user_management.UserManagementServiceConfig;
import com.zextras.carbonio.user_management.UserManagementServiceConfig.ApplicationConfig;
import com.zextras.carbonio.user_management.record.UserMyself;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Cache for {@link UserMyself}, keyed by auth token (one entry per user).
 *
 * <p>Each entry's TTL is computed at insertion time as the minimum of the config value ({@code
 * cache.usermyself-ttl}, default {@link UserManagementServiceConfig#DEFAULT_CACHE_TTL_SECONDS}) and
 * the remaining token lifetime, so an entry never outlives its token.
 *
 * <p>A reverse index {@code userIdToToken} enforces one-token-per-user: when a new token is
 * inserted for a userId that already has a cached token, the old entry is invalidated. The forward
 * index {@code tokenToUserId} provides token-to-userId resolution.
 *
 * <p>Both indexes are kept in sync via Caffeine's {@code removalListener}, which leaves them alone
 * when the cache already holds a newer entry for the same token. The cache holds at most {@link
 * UserManagementServiceConfig#CACHE_MAX_ENTRIES} entries and evicts the ones least likely to be
 * used again.
 */
@Singleton
public class UserMyselfCache {

  private static final long CONFIG_CACHE_SECONDS = 60;

  private final Cache<String, UserMyself> cache;
  private final ConcurrentHashMap<String, String> tokenToUserId;
  private final ConcurrentHashMap<String, String> userIdToToken;
  private final ApplicationConfigService configService;
  private final Clock clock;
  private final Cache<String, Long> ttlCache;

  @Inject
  public UserMyselfCache(ApplicationConfigService configService, Ticker ticker, Clock clock) {
    this(configService, ticker, clock, UserManagementServiceConfig.CACHE_MAX_ENTRIES);
  }

  UserMyselfCache(
      ApplicationConfigService configService, Ticker ticker, Clock clock, long maxEntries) {
    this.configService = configService;
    this.clock = clock;
    this.tokenToUserId = new ConcurrentHashMap<>();
    this.userIdToToken = new ConcurrentHashMap<>();
    this.ttlCache =
        Caffeine.newBuilder()
            .ticker(ticker)
            .expireAfterWrite(CONFIG_CACHE_SECONDS, TimeUnit.SECONDS)
            .build();

    this.cache =
        Caffeine.newBuilder()
            .ticker(ticker)
            .executor(Runnable::run)
            .maximumSize(maxEntries)
            .expireAfter(
                Expiry.<String, UserMyself>creating((k, v) -> Duration.ofNanos(Long.MAX_VALUE)))
            .removalListener(
                (String key, UserMyself value, RemovalCause cause) -> {
                  if (key != null && !holdsToken(key)) {
                    String userId = tokenToUserId.remove(key);
                    if (userId != null) {
                      userIdToToken.remove(userId, key);
                    }
                  }
                })
            .build();
  }

  private boolean holdsToken(String token) {
    return cache.policy().getIfPresentQuietly(token) != null;
  }

  public Optional<UserMyself> getByToken(String token) {
    return Optional.ofNullable(cache.getIfPresent(token));
  }

  public Optional<String> resolveUserId(String token) {
    return Optional.ofNullable(tokenToUserId.get(token));
  }

  public void put(String token, String userId, UserMyself myself, long expiresAt) {
    long remainingMs = Math.max(0, expiresAt - clock.millis());
    Duration ttl = capTtl(remainingMs);

    cache.policy().expireVariably().orElseThrow().put(token, myself, ttl);
    tokenToUserId.put(token, userId);

    // Atomically update userId→token index; capture displaced token (if different)
    String[] displaced = {null};
    userIdToToken.compute(
        userId,
        (uid, oldToken) -> {
          if (oldToken != null && !oldToken.equals(token)) {
            displaced[0] = oldToken;
          }
          return token;
        });

    // Invalidate displaced token outside compute() to avoid reentrant lock from removal listener
    if (displaced[0] != null) {
      cache.invalidate(displaced[0]);
    }
  }

  public void invalidateByToken(String token) {
    cache.invalidate(token);
  }

  void clearAll() {
    cache.invalidateAll();
    tokenToUserId.clear();
    userIdToToken.clear();
  }

  public boolean isCacheEnabled() {
    return readTtlSeconds() > 0;
  }

  private long readTtlSeconds() {
    return ttlCache.get(
        "usermyself-ttl",
        k ->
            configService
                .get(ApplicationConfig.CACHE_USERMYSELF_TTL)
                .map(Long::parseLong)
                .orElse(UserManagementServiceConfig.DEFAULT_CACHE_TTL_SECONDS));
  }

  /**
   * Returns an epoch-millis expiration timestamp for a new entry, computed as {@code now +
   * min(configTtl, sessionRemainingMs)}. Used by the service layer to pass as {@code expiresAt} to
   * {@link #put}.
   */
  public long computeExpiresAt(long sessionRemainingMs) {
    return clock.millis() + capTtl(sessionRemainingMs).toMillis();
  }

  private Duration capTtl(long remainingMs) {
    return min(Duration.ofSeconds(readTtlSeconds()), Duration.ofMillis(remainingMs));
  }

  private static Duration min(Duration a, Duration b) {
    return a.compareTo(b) < 0 ? a : b;
  }
}
