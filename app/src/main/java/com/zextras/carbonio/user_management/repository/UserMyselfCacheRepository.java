// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zextras.carbonio.user_management.entity.UserMyselfCacheEntity;
import com.zextras.carbonio.user_management.record.UserMyself;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class UserMyselfCacheRepository
    implements PanacheRepositoryBase<UserMyselfCacheEntity, String> {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Inject
  Clock clock;

  public record CachedUserMyself(UserMyself myself, long expiresAt) {}

  public record TokenLookupResult(String userId, UserMyself myself, long expiresAt) {}

  public Optional<CachedUserMyself> findByUserId(String userId) {
    return find("userId = ?1 and expiresAt > ?2", userId, clock.millis())
        .firstResultOptional()
        .map(e -> new CachedUserMyself(toRecord(e), e.expiresAt));
  }

  public Optional<TokenLookupResult> findByToken(String token) {
    String hash = hashToken(token);
    return find("tokenHash = ?1 and expiresAt > ?2", hash, clock.millis())
        .firstResultOptional()
        .map(e -> new TokenLookupResult(e.userId, toRecord(e), e.expiresAt));
  }

  @Transactional
  public UserMyself upsert(String userId, String token, UserMyself myself, long expiresAt) {
    String hash = hashToken(token);
    int rows = getEntityManager().createNamedQuery(UserMyselfCacheEntity.UPSERT_IF_NEWER)
        .setParameter("userId", userId)
        .setParameter("tokenHash", hash)
        .setParameter("locale", myself.locale())
        .setParameter("features", serializeFeatureList(myself.featureList()))
        .setParameter("email", myself.email())
        .setParameter("fullName", myself.fullName())
        .setParameter("domain", myself.domain())
        .setParameter("status", myself.status())
        .setParameter("type", myself.type())
        .setParameter("expiresAt", expiresAt)
        .executeUpdate();

    if (rows > 0) {
      return myself;
    }
    UserMyselfCacheEntity existing = findById(userId);
    return existing != null ? toRecord(existing) : myself;
  }

  @Transactional
  public int deleteExpired() {
    return (int) delete("expiresAt <= ?1", clock.millis());
  }

  private static UserMyself toRecord(UserMyselfCacheEntity e) {
    return new UserMyself(
        e.userId, e.email, e.fullName, e.domain,
        e.status, e.type, e.locale, e.featureList);
  }

  private static String serializeFeatureList(Map<String, Boolean> featureList) {
    try {
      return OBJECT_MAPPER.writeValueAsString(featureList);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize feature list", e);
    }
  }

  static String hashToken(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
