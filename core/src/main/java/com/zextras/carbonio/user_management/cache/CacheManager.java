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
import com.zextras.carbonio.user_management.generated.model.UserIdDto;
import com.zextras.carbonio.user_management.generated.model.UserInfoDto;
import java.util.concurrent.TimeUnit;

@Singleton
public class CacheManager {

  private Cache<UserIdDto, UserInfoDto> userByUUIDCache;
  private Cache<String, UserInfoDto>  userByEmailCache;
  private Cache<String, UserToken> userTokenCache;

  @Inject
  public CacheManager() {

    userByUUIDCache = Caffeine
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

  public Cache<UserIdDto, UserInfoDto> getUserByIdCache() {
    return userByUUIDCache;
  }

  public Cache<String, UserInfoDto> getUserByEmailCache() {
    return userByEmailCache;
  }

  public Cache<String, UserToken> getUserTokenCache() {
    return userTokenCache;
  }
}
