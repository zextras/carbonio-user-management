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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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
    String hash = hashToken(token);
    return find("tokenHash = ?1 and expiresAt > ?2", hash, System.currentTimeMillis())
        .firstResultOptional()
        .map(e -> new TokenLookupResult(e.userId, toRecord(e), e.expiresAt));
  }

  public Optional<String> resolveUserId(String token) {
    String hash = hashToken(token);
    return find("tokenHash = ?1 and expiresAt > ?2", hash, System.currentTimeMillis())
        .firstResultOptional()
        .map(e -> e.userId);
  }

  @Transactional
  public UserDetails upsert(String userId, String token, UserDetails details, long expiresAt) {
    String hash = hashToken(token);
    int rows = getEntityManager().createNamedQuery(UserDetailsCacheEntity.UPSERT_IF_NEWER)
        .setParameter("userId", userId)
        .setParameter("tokenHash", hash)
        .setParameter("locale", details.locale())
        .setParameter("features", serializeFeatureList(details.featureList()))
        .setParameter("expiresAt", expiresAt)
        .executeUpdate();

    if (rows > 0) {
      return details;
    }
    // DB has a newer expiresAt — read back its data. If the row was concurrently deleted
    // (e.g. by cleanup), fall back to the fresh data we already have from SOAP.
    UserDetailsCacheEntity existing = findById(userId);
    return existing != null ? toRecord(existing) : details;
  }

  @Transactional
  public int deleteExpired() {
    return (int) delete("expiresAt <= ?1", System.currentTimeMillis());
  }

  private static UserDetails toRecord(UserDetailsCacheEntity e) {
    return new UserDetails(e.locale, e.featureList);
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
