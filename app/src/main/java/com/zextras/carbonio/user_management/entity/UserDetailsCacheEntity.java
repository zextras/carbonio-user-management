// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.cache.entity;

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
@Table(name = "user_details_cache")
@NamedNativeQuery(
    name = UserDetailsCacheEntity.UPSERT_IF_NEWER,
    query = """
        INSERT INTO user_details_cache (user_id, token, locale, feature_list, expires_at)
        VALUES (:userId, :token, :locale, CAST(:features AS JSONB), :expiresAt)
        ON CONFLICT (user_id) DO UPDATE SET
          token = EXCLUDED.token,
          locale = EXCLUDED.locale,
          feature_list = EXCLUDED.feature_list,
          expires_at = EXCLUDED.expires_at
        WHERE user_details_cache.expires_at < EXCLUDED.expires_at
        """
)
public class UserDetailsCacheEntity extends PanacheEntityBase {

  public static final String UPSERT_IF_NEWER = "UserDetailsCache.upsertIfNewer";

  @Id
  @Column(name = "user_id", length = 64)
  public String userId;

  @Column(columnDefinition = "text")
  public String token;

  @Column(nullable = false, length = 32)
  public String locale;

  @Column(name = "feature_list", nullable = false, columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  public Map<String, Boolean> featureList = new HashMap<>();

  @Column(name = "expires_at", nullable = false)
  public long expiresAt;
}
