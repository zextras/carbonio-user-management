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
import com.zextras.carbonio.user_management.record.UserInfo;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Cache for {@link UserInfo}, keyed by userId with a secondary index by email.
 *
 * <p>Each entry's TTL is read from config ({@code cache.userinfo-ttl}) at insertion time via
 * Caffeine's {@code VarExpiration} API, so config changes on Consul take effect immediately without
 * restart.
 *
 * <p>Caffeine doesn't support multi-key lookup natively. The secondary index ({@code
 * emailToUserId}) is a ConcurrentHashMap kept in sync via Caffeine's {@code removalListener}: when
 * an entry expires, is evicted or is invalidated, the listener removes the corresponding email
 * mapping unless the cache already holds a newer entry with that email.
 *
 * <p>The cache holds at most {@link UserManagementServiceConfig#CACHE_MAX_ENTRIES} entries and
 * evicts the ones least likely to be used again.
 */
@Singleton
public class UserInfoCache {

  private static final long CONFIG_CACHE_SECONDS = 60;

  private final Cache<String, UserInfo> cache;
  private final ConcurrentHashMap<String, String> emailToUserId;
  private final ApplicationConfigService configService;
  private final Cache<String, Long> ttlCache;

  @Inject
  public UserInfoCache(ApplicationConfigService configService, Ticker ticker) {
    this(configService, ticker, UserManagementServiceConfig.CACHE_MAX_ENTRIES);
  }

  UserInfoCache(ApplicationConfigService configService, Ticker ticker, long maxEntries) {
    this.configService = configService;
    this.emailToUserId = new ConcurrentHashMap<>();
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
                Expiry.<String, UserInfo>creating((k, v) -> Duration.ofNanos(Long.MAX_VALUE)))
            .removalListener(
                (String key, UserInfo value, RemovalCause cause) -> {
                  if (key != null
                      && value != null
                      && value.email() != null
                      && !holdsEmail(key, value.email())) {
                    emailToUserId.remove(value.email(), key);
                  }
                })
            .build();
  }

  private boolean holdsEmail(String userId, String email) {
    UserInfo current = cache.policy().getIfPresentQuietly(userId);
    return current != null && email.equals(current.email());
  }

  public Optional<UserInfo> getByUserId(String userId) {
    return Optional.ofNullable(cache.getIfPresent(userId));
  }

  public Optional<UserInfo> getByEmail(String email) {
    String userId = emailToUserId.get(email);
    if (userId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(cache.getIfPresent(userId))
        .filter(userInfo -> email.equals(userInfo.email()));
  }

  public void put(UserInfo userInfo) {
    long ttlSeconds = readTtlSeconds();
    cache
        .policy()
        .expireVariably()
        .orElseThrow()
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

  public boolean isCacheEnabled() {
    return readTtlSeconds() > 0;
  }

  public long readTtlSeconds() {
    return ttlCache.get(
        "userinfo-ttl",
        k ->
            configService
                .get(ApplicationConfig.CACHE_USERINFO_TTL)
                .map(Long::parseLong)
                .orElse(UserManagementServiceConfig.DEFAULT_CACHE_TTL_SECONDS));
  }
}
