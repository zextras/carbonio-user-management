// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.cache.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zextras.carbonio.user_management.cache.entity.UserDetailsCacheEntity;
import com.zextras.carbonio.user_management.cache.record.UserDetails;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class UserDetailsCacheRepository
    implements PanacheRepositoryBase<UserDetailsCacheEntity, String> {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public record CachedUserDetails(UserDetails details, long expiresAt) {}

  public record TokenLookupResult(String userId, UserDetails details, long expiresAt) {}

  public Optional<CachedUserDetails> findByUserId(String userId) {
    return find("userId = ?1 and expiresAt > ?2", userId, System.currentTimeMillis())
        .firstResultOptional()
        .map(e -> new CachedUserDetails(toRecord(e), e.expiresAt));
  }

  public Optional<TokenLookupResult> findByToken(String token) {
    return find("token = ?1 and expiresAt > ?2", token, System.currentTimeMillis())
        .firstResultOptional()
        .map(e -> new TokenLookupResult(e.userId, toRecord(e), e.expiresAt));
  }

  public Optional<String> resolveUserId(String token) {
    return find("token = ?1 and expiresAt > ?2", token, System.currentTimeMillis())
        .firstResultOptional()
        .map(e -> e.userId);
  }

  @Transactional
  public UserDetails upsert(String userId, String token, UserDetails details, long expiresAt) {
    int rows = getEntityManager().createNativeQuery("""
            INSERT INTO user_details_cache (user_id, token, locale, carbonio_attributes, expires_at)
            VALUES (:userId, :token, :locale, CAST(:attrs AS JSONB), :expiresAt)
            ON CONFLICT (user_id) DO UPDATE SET
              token = EXCLUDED.token,
              locale = EXCLUDED.locale,
              carbonio_attributes = EXCLUDED.carbonio_attributes,
              expires_at = EXCLUDED.expires_at
            WHERE user_details_cache.expires_at < EXCLUDED.expires_at
            """)
        .setParameter("userId", userId)
        .setParameter("token", token)
        .setParameter("locale", details.locale())
        .setParameter("attrs", serializeAttributes(details.carbonioAttributes()))
        .setParameter("expiresAt", expiresAt)
        .executeUpdate();

    if (rows > 0) {
      return details;
    }
    // DB has a newer value — read it back (no expiry filter needed)
    UserDetailsCacheEntity entity = findById(userId);
    return entity != null ? toRecord(entity) : details;
  }

  @Transactional
  public int deleteExpired() {
    return (int) delete("expiresAt <= ?1", System.currentTimeMillis());
  }

  private static UserDetails toRecord(UserDetailsCacheEntity e) {
    return new UserDetails(e.locale, e.carbonioAttributes);
  }

  private static String serializeAttributes(Map<String, String> attributes) {
    try {
      return OBJECT_MAPPER.writeValueAsString(attributes);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize carbonio attributes", e);
    }
  }
}
