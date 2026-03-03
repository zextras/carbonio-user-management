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
import java.util.HashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "user_myself_cache")
@NamedNativeQuery(
    name = UserMyselfCacheEntity.UPSERT_IF_NEWER,
    query = """
        INSERT INTO user_myself_cache
          (user_id, token_hash, locale, feature_list, email, full_name, domain, status, type, expires_at)
        VALUES
          (:userId, :tokenHash, :locale, CAST(:features AS JSONB), :email, :fullName, :domain, :status, :type, :expiresAt)
        ON CONFLICT (user_id) DO UPDATE SET
          token_hash = EXCLUDED.token_hash,
          locale = EXCLUDED.locale,
          feature_list = EXCLUDED.feature_list,
          email = EXCLUDED.email,
          full_name = EXCLUDED.full_name,
          domain = EXCLUDED.domain,
          status = EXCLUDED.status,
          type = EXCLUDED.type,
          expires_at = EXCLUDED.expires_at
        WHERE user_myself_cache.expires_at < EXCLUDED.expires_at
        """
)
public class UserMyselfCacheEntity extends PanacheEntityBase {

  public static final String UPSERT_IF_NEWER = "UserMyselfCache.upsertIfNewer";

  @Id
  @Column(name = "user_id", length = 64)
  public String userId;

  @Column(name = "token_hash", length = 64, unique = true)
  public String tokenHash;

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

  @Column(nullable = false, length = 32)
  public String locale;

  @Column(name = "feature_list", nullable = false, columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  public Map<String, Boolean> featureList = new HashMap<>();

  @Column(name = "expires_at", nullable = false)
  public long expiresAt;
}
