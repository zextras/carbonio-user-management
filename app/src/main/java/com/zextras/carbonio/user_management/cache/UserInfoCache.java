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
import com.zextras.carbonio.user_management.record.UserInfo;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Cache for {@link UserInfo}, keyed by userId with a secondary index by email.
 *
 * <p>Each entry's TTL is read from config ({@code cache.userinfo-ttl}) at insertion time via
 * Caffeine's {@code VarExpiration} API, so config changes on Consul take effect immediately
 * without restart.
 *
 * <p>Caffeine doesn't support multi-key lookup natively. The secondary index
 * ({@code emailToUserId}) is a ConcurrentHashMap kept in sync via Caffeine's
 * {@code removalListener}: when an entry expires or is invalidated, the listener automatically
 * removes the corresponding email mapping.
 */
@Singleton
public class UserInfoCache {

  private static final long CONFIG_CACHE_SECONDS = 60;

  private final Cache<String, UserInfo> cache;
  private final ConcurrentHashMap<String, String> emailToUserId;
  private final ApplicationConfigService configService;
  private final Clock clock;
  private final Cache<String, Long> ttlCache;

  @Inject
  public UserInfoCache(ApplicationConfigService configService) {
    this(configService, Ticker.systemTicker(), Clock.systemUTC());
  }

  UserInfoCache(ApplicationConfigService configService, Ticker ticker, Clock clock) {
    this.configService = configService;
    this.clock = clock;
    this.emailToUserId = new ConcurrentHashMap<>();
    this.ttlCache = Caffeine.newBuilder()
        .expireAfterWrite(CONFIG_CACHE_SECONDS, TimeUnit.SECONDS)
        .build();

    this.cache = Caffeine.newBuilder()
        .ticker(ticker)
        .expireAfter(Expiry.<String, UserInfo>creating((k, v) -> Duration.ofNanos(Long.MAX_VALUE)))
        .removalListener((key, value, cause) -> {
          if (value != null && value.email() != null) {
            emailToUserId.remove(value.email());
          }
        })
        .build();
  }

  public Optional<UserInfo> getByUserId(String userId) {
    return Optional.ofNullable(cache.getIfPresent(userId));
  }

  public Optional<UserInfo> getByEmail(String email) {
    String userId = emailToUserId.get(email);
    if (userId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(cache.getIfPresent(userId));
  }

  public void put(UserInfo userInfo) {
    long ttlSeconds = readTtlSeconds();
    cache.policy().expireVariably().orElseThrow()
        .put(userInfo.userId(), userInfo, Duration.ofSeconds(ttlSeconds));
    if (userInfo.email() != null) {
      emailToUserId.put(userInfo.email(), userInfo.userId());
    }
  }

  public void invalidate(String userId) {
    cache.invalidate(userId);
  }

  void clearAll() {
    cache.invalidateAll();
    emailToUserId.clear();
  }

  /**
   * Returns an epoch-millis expiration timestamp for a new entry, computed as
   * {@code now + configuredTtl}. Used by the service layer to persist entries in the shared DB.
   */
  public long computeExpiresAt() {
    return clock.millis() + readTtlSeconds() * 1000;
  }

  public boolean isCacheEnabled() {
    return readTtlSeconds() > 0;
  }

  public long readTtlSeconds() {
    return ttlCache.get("userinfo-ttl", k ->
        Long.parseLong(
            configService.get(ApplicationConfig.CACHE_USERINFO_TTL)
                .orElseThrow(() -> new IllegalStateException(
                    "Missing required config: " + ApplicationConfig.CACHE_USERINFO_TTL))));
  }
}
