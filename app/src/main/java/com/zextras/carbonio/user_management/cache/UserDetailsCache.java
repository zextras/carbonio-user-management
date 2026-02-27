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
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class UserDetailsCache {

  private final Cache<String, UserDetails> cache;
  private final ConcurrentHashMap<String, String> tokenToUserId;
  private final ApplicationConfigService configService;

  @Inject
  public UserDetailsCache(ApplicationConfigService configService) {
    this(configService, Ticker.systemTicker());
  }

  UserDetailsCache(ApplicationConfigService configService, Ticker ticker) {
    this.configService = configService;
    this.tokenToUserId = new ConcurrentHashMap<>();

    this.cache = Caffeine.newBuilder()
        .ticker(ticker)
        .expireAfter(Expiry.<String, UserDetails>creating((k, v) -> Duration.ofNanos(Long.MAX_VALUE)))
        .removalListener((key, value, cause) ->
            tokenToUserId.entrySet().removeIf(entry -> entry.getValue().equals(key)))
        .build();
  }

  public Optional<UserDetails> getByUserId(String userId) {
    return Optional.ofNullable(cache.getIfPresent(userId));
  }

  public Optional<UserDetails> getByToken(String token) {
    String userId = tokenToUserId.get(token);
    if (userId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(cache.getIfPresent(userId));
  }

  public Optional<String> resolveUserId(String token) {
    return Optional.ofNullable(tokenToUserId.get(token));
  }

  public void put(String userId, String token, UserDetails details, long remainingMs) {
    Duration ttl = computeTtl(remainingMs);
    cache.policy().expireVariably().orElseThrow().put(userId, details, ttl);
    if (token != null) {
      tokenToUserId.put(token, userId);
    }
  }

  public void invalidate(String userId) {
    cache.invalidate(userId);
  }

  private Duration computeTtl(long remainingMs) {
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
