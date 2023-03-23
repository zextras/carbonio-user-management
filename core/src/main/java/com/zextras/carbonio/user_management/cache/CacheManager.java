// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zextras.carbonio.user_management.entities.UserToken;
import com.zextras.carbonio.user_management.generated.model.UserInfo;
import java.util.concurrent.TimeUnit;

@Singleton
public class CacheManager {

  private Cache<String, UserInfo>  userByIdCache;
  private Cache<String, UserInfo>  userByEmailCache;
  private Cache<String, UserToken> userTokenCache;

  @Inject
  public CacheManager() {

    userByIdCache = Caffeine
      .newBuilder()
      .expireAfterWrite(5, TimeUnit.MINUTES)
      .build();

    userByEmailCache = Caffeine
      .newBuilder()
      .expireAfterWrite(5, TimeUnit.MINUTES)
      .build();

    userTokenCache = Caffeine
      .newBuilder()
      .expireAfter(new Expiry<String, UserToken>() {
        @Override
        public long expireAfterCreate(
          String key,
          UserToken value,
          long currentTime
        ) {
          return currentTime + value.getLifeTimeInMillis() * 1000;
        }

        @Override
        public long expireAfterUpdate(
          String key,
          UserToken value,
          long currentTime,
          long currentDuration
        ) {
          return currentTime + value.getLifeTimeInMillis() * 1000;
        }

        @Override
        public long expireAfterRead(
          String key,
          UserToken value,
          long currentTime,
          long currentDuration
        ) {
          return currentTime + value.getLifeTimeInMillis() * 1000;
        }
      })
      .build();
  }

  public Cache<String, UserInfo> getUserByIdCache() {
    return userByIdCache;
  }

  public Cache<String, UserInfo> getUserByEmailCache() {
    return userByEmailCache;
  }

  public Cache<String, UserToken> getUserTokenCache() {
    return userTokenCache;
  }
}
