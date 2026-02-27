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
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class UserDetailsCache {

  private final Cache<String, UserDetails> primaryCache;
  private final ConcurrentHashMap<String, String> tokenToUserId;
  private final ConcurrentHashMap<String, Long> entryTtlNanos;
  private final OptionalLong configTtlSeconds;

  @Inject
  public UserDetailsCache(ApplicationConfigService configService) {
    this(configService, Ticker.systemTicker());
  }

  UserDetailsCache(ApplicationConfigService configService, Ticker ticker) {
    this.tokenToUserId = new ConcurrentHashMap<>();
    this.entryTtlNanos = new ConcurrentHashMap<>();
    this.configTtlSeconds = configService.get(ApplicationConfig.CACHE_DETAILS_TTL)
        .map(Long::parseLong)
        .map(OptionalLong::of)
        .orElse(OptionalLong.empty());

    this.primaryCache = Caffeine.newBuilder()
        .ticker(ticker)
        .expireAfter(new Expiry<String, UserDetails>() {
          @Override
          public long expireAfterCreate(String key, UserDetails value, long currentTime) {
            Long ttl = entryTtlNanos.get(key);
            if (ttl == null) {
              throw new IllegalStateException("No TTL set for entry: " + key);
            }
            return ttl;
          }

          @Override
          public long expireAfterUpdate(String key, UserDetails value,
              long currentTime, long currentDuration) {
            return currentDuration;
          }

          @Override
          public long expireAfterRead(String key, UserDetails value,
              long currentTime, long currentDuration) {
            return currentDuration;
          }
        })
        .removalListener((key, value, cause) -> {
          if (key != null) {
            entryTtlNanos.remove(key);
          }
          tokenToUserId.entrySet().removeIf(entry -> entry.getValue().equals(key));
        })
        .build();
  }

  public Optional<UserDetails> getByUserId(String userId) {
    return Optional.ofNullable(primaryCache.getIfPresent(userId));
  }

  public Optional<UserDetails> getByToken(String token) {
    String userId = tokenToUserId.get(token);
    if (userId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(primaryCache.getIfPresent(userId));
  }

  public Optional<String> resolveUserId(String token) {
    return Optional.ofNullable(tokenToUserId.get(token));
  }

  public void put(String userId, String token, UserDetails details, long remainingMs) {
    long ttlNanos = computeTtlNanos(remainingMs);
    entryTtlNanos.put(userId, ttlNanos);
    primaryCache.put(userId, details);
    if (token != null) {
      tokenToUserId.put(token, userId);
    }
  }

  public void invalidate(String userId) {
    primaryCache.invalidate(userId);
  }

  private long computeTtlNanos(long remainingMs) {
    long remainingNanos = Duration.ofMillis(remainingMs).toNanos();

    if (configTtlSeconds.isPresent()) {
      long configNanos = Duration.ofSeconds(configTtlSeconds.getAsLong()).toNanos();
      return Math.min(configNanos, remainingNanos);
    }

    return remainingNanos;
  }
}
