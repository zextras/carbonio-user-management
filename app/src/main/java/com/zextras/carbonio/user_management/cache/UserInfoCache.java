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
import com.zextras.carbonio.user_management.cache.record.UserInfo;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class UserInfoCache {

  private final Cache<String, UserInfo> cache;
  private final ConcurrentHashMap<String, String> emailToUserId;
  private final ApplicationConfigService configService;

  @Inject
  public UserInfoCache(ApplicationConfigService configService) {
    this(configService, Ticker.systemTicker());
  }

  UserInfoCache(ApplicationConfigService configService, Ticker ticker) {
    this.configService = configService;
    this.emailToUserId = new ConcurrentHashMap<>();

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
    long ttlSeconds = Long.parseLong(
        configService.get(ApplicationConfig.CACHE_USERINFO_TTL)
            .orElseThrow(() -> new IllegalStateException(
                "Missing required config: " + ApplicationConfig.CACHE_USERINFO_TTL)));
    cache.policy().expireVariably().orElseThrow()
        .put(userInfo.userId(), userInfo, Duration.ofSeconds(ttlSeconds));
    if (userInfo.email() != null) {
      emailToUserId.put(userInfo.email(), userInfo.userId());
    }
  }

  public void invalidate(String userId) {
    cache.invalidate(userId);
  }
}
