// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.service;

import com.zextras.carbonio.user_management.cache.UserInfoCache;
import com.zextras.carbonio.user_management.cache.UserMyselfCache;
import com.zextras.carbonio.user_management.record.UserInfo;
import com.zextras.carbonio.user_management.record.UserMyself;
import com.zextras.carbonio.user_management.service.UserDirectory.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class UserService {

  private static final Logger logger = LoggerFactory.getLogger(UserService.class);

  private final UserDirectory directory;
  private final UserInfoCache userInfoCache;
  private final UserMyselfCache userMyselfCache;

  // Coalescing maps: prevent concurrent calls for the same key.
  private final ConcurrentHashMap<String, CompletableFuture<Optional<UserInfo>>> inflightById =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, CompletableFuture<Optional<UserInfo>>> inflightByEmail =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, CompletableFuture<Optional<UserMyself>>> inflightMyself =
      new ConcurrentHashMap<>();

  @Inject
  public UserService(
      UserDirectory directory, UserInfoCache userInfoCache, UserMyselfCache userMyselfCache) {
    this.directory = directory;
    this.userInfoCache = userInfoCache;
    this.userMyselfCache = userMyselfCache;
  }

  /**
   * Resolves the caller identity from its auth token.
   *
   * <p>When {@code bypassCache} is true the cached entry is not read, so the token is always
   * re-validated against the directory: this is what a caller asks for when it cannot tolerate a
   * revoked session surviving in cache for the remaining token lifetime. The freshly fetched
   * result is still written back to the cache, so a bypass refreshes the entry rather than
   * disabling caching for everyone else.
   */
  public Optional<UserMyself> getUserMyself(String token, boolean bypassCache) {
    logger.debug("GetUserMyself requested (bypassCache={})", bypassCache);

    if (!bypassCache) {
      Optional<UserMyself> cached = userMyselfCache.getByToken(token);
      if (cached.isPresent()) {
        logger.debug("GetUserMyself cache hit");
        safeWarmUserInfoCache(cached.get());
        return cached;
      }
    }

    // Coalesce concurrent lookups for the same token
    Optional<UserMyself> result = coalesce(inflightMyself, token, () -> loadUserMyself(token));
    result.ifPresent(this::safeWarmUserInfoCache);
    return result;
  }

  private void safeWarmUserInfoCache(UserMyself myself) {
    try {
      warmUserInfoCacheIfAbsent(myself);
    } catch (Exception e) {
      logger.warn("Warming UserInfo cache failed for userId={}, ignoring", myself.userId(), e);
    }
  }

  private void warmUserInfoCacheIfAbsent(UserMyself myself) {
    String userId = myself.userId();
    if (userInfoCache.getByUserId(userId).isPresent()) {
      return; // already cached
    }
    UserInfo userInfo =
        new UserInfo(
            myself.userId(),
            myself.email(),
            myself.fullName(),
            myself.domain(),
            myself.status(),
            myself.type(),
            myself.cosId(),
            myself.domainId(),
            myself.isGlobalAdmin());
    cacheInfo(userInfo);
  }

  private Optional<UserMyself> loadUserMyself(String token) {
    Optional<AuthenticatedUser> authenticated = directory.byToken(token);
    if (authenticated.isEmpty()) {
      logger.debug("GetUserMyself: token rejected");
      return Optional.empty();
    }
    UserMyself myself = authenticated.get().myself();
    long expiresAt = userMyselfCache.computeExpiresAt(authenticated.get().sessionLifetimeMs());
    myself = cacheMyself(myself.userId(), token, myself, expiresAt);
    logger.debug("GetUserMyself resolved from the directory for userId {}", myself.userId());
    return Optional.of(myself);
  }

  public Optional<UserInfo> getUserById(String userId) {
    logger.debug("GetUserById requested: {}", userId);

    Optional<UserInfo> cached = userInfoCache.getByUserId(userId);
    if (cached.isPresent()) {
      return cached;
    }

    // Coalesce concurrent lookups for the same userId
    return coalesce(
        inflightById, userId, () -> directory.byId(userId).map(this::cacheInfo));
  }

  public Optional<UserInfo> getUserByEmail(String email) {
    logger.debug("GetUserByEmail requested: {}", email);

    Optional<UserInfo> cached = userInfoCache.getByEmail(email);
    if (cached.isPresent()) {
      return cached;
    }

    // Coalesce concurrent lookups for the same email
    return coalesce(
        inflightByEmail, email, () -> directory.byEmail(email).map(this::cacheInfo));
  }

  public List<UserInfo> getUsers(List<String> userIds) {
    List<String> uniqueIds = userIds.stream().distinct().toList();
    Map<String, UserInfo> results = new HashMap<>();

    List<String> misses = new ArrayList<>();
    for (String userId : uniqueIds) {
      userInfoCache
          .getByUserId(userId)
          .ifPresentOrElse(info -> results.put(userId, info), () -> misses.add(userId));
    }

    if (!misses.isEmpty()) {
      for (UserInfo info : directory.byIds(misses)) {
        results.put(info.userId(), cacheInfo(info));
      }
    }

    // Return in original order, skipping not-found
    return uniqueIds.stream().filter(results::containsKey).map(results::get).toList();
  }

  // -- Cache helpers --

  private UserInfo cacheInfo(UserInfo userInfo) {
    if (!userInfoCache.isCacheEnabled()) {
      return userInfo;
    }
    userInfoCache.put(userInfo);
    return userInfo;
  }

  private UserMyself cacheMyself(String userId, String token, UserMyself myself, long expiresAt) {
    if (!userMyselfCache.isCacheEnabled()) {
      return myself;
    }
    userMyselfCache.put(token, userId, myself, expiresAt);
    return myself;
  }

  private static final long COALESCE_TIMEOUT_SECONDS = 5;

  /**
   * Deduplicates concurrent lookups for the same key. Secondary threads wait up to {@link
   * #COALESCE_TIMEOUT_SECONDS} seconds before giving up.
   */
  private <T> T coalesce(
      ConcurrentHashMap<String, CompletableFuture<T>> inflight, String key, Supplier<T> loader) {
    CompletableFuture<T> future = new CompletableFuture<>();
    CompletableFuture<T> existing = inflight.putIfAbsent(key, future);
    if (existing != null) {
      try {
        return existing.get(COALESCE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      } catch (TimeoutException e) {
        logger.warn("Coalesce timeout waiting for key={}, proceeding independently", key);
        return loader.get();
      } catch (java.util.concurrent.ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof RuntimeException re) throw re;
        throw new RuntimeException(cause);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }
    try {
      T result = loader.get();
      future.complete(result);
      return result;
    } catch (Exception e) {
      future.completeExceptionally(e);
      throw e;
    } finally {
      inflight.remove(key);
    }
  }
}
