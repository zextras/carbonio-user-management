// SPDX-FileCopyrightText: 2024 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.cache;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.benmanes.caffeine.cache.Cache;
import com.zextras.carbonio.user_management.entities.UserToken;

class CacheManagerTest {

  private CacheManager cacheManager;

  @BeforeEach
  void setUp() {
    cacheManager = new CacheManager();
  }

  @Test
  void givenATokenCacheWithAValidItemTheGetItemShouldReturnTheUserToken() {
    // Given
    Cache<String, UserToken> userTokenCache = cacheManager.getUserTokenCache();
    userTokenCache.put("fake-token", new UserToken("fake-token", "fake-user-id", 100000L));

    // When
    UserToken userToken = userTokenCache.getIfPresent("fake-token");

    // Then
    Assertions.assertThat(userToken).isNotNull();
    Assertions.assertThat(userToken.getToken()).isEqualTo("fake-token");
  }

  @Test
  void givenATokenCacheWithAnExpiredItemTheGetItemShouldReturnNull() {
    // Given
    Cache<String, UserToken> userTokenCache = cacheManager.getUserTokenCache();
    userTokenCache.put("fake-token", new UserToken("fake-token", "fake-user-id", 10L));

    // Waiting for the item expiration
    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(200));

    // When
    UserToken userToken = userTokenCache.getIfPresent("fake-token");

    // Then
    Assertions.assertThat(userToken).isNull();
  }
}
