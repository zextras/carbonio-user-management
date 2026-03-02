// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.cache.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.HashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "user_details_cache")
public class UserDetailsCacheEntity extends PanacheEntityBase {

  @Id
  @Column(name = "user_id", length = 64)
  public String userId;

  @Column(columnDefinition = "text")
  public String token;

  @Column(nullable = false, length = 32)
  public String locale;

  @Column(name = "carbonio_attributes", nullable = false, columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  public Map<String, String> carbonioAttributes = new HashMap<>();

  @Column(name = "expires_at", nullable = false)
  public long expiresAt;
}
