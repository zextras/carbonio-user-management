// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.NamedNativeQuery;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_info_cache")
@NamedNativeQuery(
    name = UserInfoCacheEntity.UPSERT_IF_NEWER,
    query = """
        INSERT INTO user_info_cache (user_id, email, full_name, domain, status, type, expires_at)
        VALUES (:userId, :email, :fullName, :domain, :status, :type, :expiresAt)
        ON CONFLICT (user_id) DO UPDATE SET
          email = EXCLUDED.email,
          full_name = EXCLUDED.full_name,
          domain = EXCLUDED.domain,
          status = EXCLUDED.status,
          type = EXCLUDED.type,
          expires_at = EXCLUDED.expires_at
        WHERE user_info_cache.expires_at < EXCLUDED.expires_at
        """
)
public class UserInfoCacheEntity extends PanacheEntityBase {

  public static final String UPSERT_IF_NEWER = "UserInfoCache.upsertIfNewer";

  @Id
  @Column(name = "user_id", length = 64)
  public String userId;

  @Column(length = 320)
  public String email;

  @Column(name = "full_name", nullable = false, length = 512)
  public String fullName;

  @Column(length = 255)
  public String domain;

  @Column(nullable = false, length = 32)
  public String status;

  @Column(nullable = false, length = 32)
  public String type;

  @Column(name = "expires_at", nullable = false)
  public long expiresAt;
}
