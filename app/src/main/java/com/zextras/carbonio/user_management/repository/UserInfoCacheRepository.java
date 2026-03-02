// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.cache.repository;

import com.zextras.carbonio.user_management.cache.entity.UserInfoCacheEntity;
import com.zextras.carbonio.user_management.cache.record.UserInfo;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.Optional;

@ApplicationScoped
public class UserInfoCacheRepository implements PanacheRepositoryBase<UserInfoCacheEntity, String> {

  public Optional<UserInfo> findByUserId(String userId) {
    return find("userId = ?1 and expiresAt > ?2", userId, System.currentTimeMillis())
        .firstResultOptional()
        .map(UserInfoCacheRepository::toRecord);
  }

  public Optional<UserInfo> findByEmail(String email) {
    return find("email = ?1 and expiresAt > ?2", email, System.currentTimeMillis())
        .firstResultOptional()
        .map(UserInfoCacheRepository::toRecord);
  }

  @Transactional
  public UserInfo upsert(UserInfo userInfo, long expiresAt) {
    int rows = getEntityManager().createNamedQuery(UserInfoCacheEntity.UPSERT_IF_NEWER)
        .setParameter("userId", userInfo.userId())
        .setParameter("email", userInfo.email())
        .setParameter("fullName", userInfo.fullName())
        .setParameter("domain", userInfo.domain())
        .setParameter("status", userInfo.status())
        .setParameter("type", userInfo.type())
        .setParameter("expiresAt", expiresAt)
        .executeUpdate();

    if (rows > 0) {
      return userInfo;
    }
    // DB has a newer value — read it back (no expiry filter needed)
    UserInfoCacheEntity entity = findById(userInfo.userId());
    return entity != null ? toRecord(entity) : userInfo;
  }

  @Transactional
  public int deleteExpired() {
    return (int) delete("expiresAt <= ?1", System.currentTimeMillis());
  }

  private static UserInfo toRecord(UserInfoCacheEntity e) {
    return new UserInfo(e.userId, e.email, e.fullName, e.domain, e.status, e.type);
  }
}
