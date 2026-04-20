// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.service;

import com.zextras.carbonio.user_management.cache.UserInfoCache;
import com.zextras.carbonio.user_management.cache.UserMyselfCache;
import com.zextras.carbonio.user_management.record.UserInfo;
import com.zextras.carbonio.user_management.record.UserMyself;
import com.zextras.mailbox.client.MailboxClientException;
import com.zextras.mailbox.client.MailboxServerException;
import com.zextras.mailbox.client.internal.AccountInfo;
import com.zextras.mailbox.client.internal.MailboxInternalApiClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class UserService {

  private static final Logger logger = LoggerFactory.getLogger(UserService.class);

  private final MailboxInternalApiClient internalClient;
  private final UserInfoCache userInfoCache;
  private final UserMyselfCache userMyselfCache;
  private final ExecutorService executor;

  // Coalescing maps: prevent concurrent calls for the same key.
  private final ConcurrentHashMap<String, CompletableFuture<Optional<UserInfo>>>
      inflightById = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, CompletableFuture<Optional<UserInfo>>>
      inflightByEmail = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, CompletableFuture<Optional<UserMyself>>>
      inflightMyself = new ConcurrentHashMap<>();

  @Inject
  public UserService(
      MailboxInternalApiClient internalClient,
      UserInfoCache userInfoCache,
      UserMyselfCache userMyselfCache,
      ManagedExecutor executor
  ) {
    this.internalClient = internalClient;
    this.userInfoCache = userInfoCache;
    this.userMyselfCache = userMyselfCache;
    this.executor = executor;
  }

  public Optional<UserMyself> getUserMyself(String token) {
    logger.debug("GetUserMyself requested");

    // Caffeine cache hit
    Optional<UserMyself> cached = userMyselfCache.getByToken(token);
    if (cached.isPresent()) {
      logger.debug("GetUserMyself cache hit");
      safeWarmUserInfoCache(cached.get());
      return cached;
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
      logger.warn("Warming UserInfo cache failed for userId={}, ignoring",
          myself.userId(), e);
    }
  }

  private void warmUserInfoCacheIfAbsent(UserMyself myself) {
    String userId = myself.userId();
    if (userInfoCache.getByUserId(userId).isPresent()) {
      return;  // already cached
    }
    UserInfo userInfo = new UserInfo(
        myself.userId(), myself.email(), myself.fullName(),
        myself.domain(), myself.status(), myself.type());
    cacheInfo(userInfo);
  }

  private Optional<UserMyself> loadUserMyself(String token) {
    try {
      AccountInfo info = sendWithTimeout(() -> internalClient.getMyAccountInfo(token));

      UserMyself myself = mapAccountInfoToUserMyself(info);
      if (myself.userId() == null || myself.email() == null) {
        logger.error("GetUserMyself: mailbox response missing userId or email");
        return Optional.empty();
      }
      long expiresAt = info.sessionLifetimeMs() != null
          ? userMyselfCache.computeExpiresAt(info.sessionLifetimeMs())
          : userMyselfCache.computeExpiresAt(3_600_000L);

      myself = cacheMyself(myself.userId(), token, myself, expiresAt);

      logger.debug("GetUserMyself fetched from mailbox for userId {}", myself.userId());
      return Optional.of(myself);

    } catch (TimeoutException e) {
      logger.error("GetUserMyself timed out after {}s", MAILBOX_TIMEOUT_SECONDS);
      return Optional.empty();
    } catch (MailboxServerException e) {
      logger.error("GetUserMyself server error", e);
      return Optional.empty();
    } catch (MailboxClientException e) {
      logger.error("GetUserMyself client error", e);
      return Optional.empty();
    }
  }

  public Optional<UserInfo> getUserById(String userId) {
    logger.debug("GetUserById requested: {}", userId);

    // Caffeine cache hit
    Optional<UserInfo> cached = userInfoCache.getByUserId(userId);
    if (cached.isPresent()) {
      return cached;
    }

    // Coalesce concurrent lookups for the same userId
    return coalesce(inflightById, userId, () -> loadUserById(userId));
  }

  private Optional<UserInfo> loadUserById(String userId) {
    try {
      AccountInfo info = sendWithTimeout(() -> internalClient.getAccountInfo(userId));

      UserInfo userInfo = mapAccountInfoToUserInfo(info);
      if (userInfo.userId() == null || userInfo.email() == null) {
        logger.error("GetUserById: mailbox response missing userId or email for {}", userId);
        return Optional.empty();
      }
      userInfo = cacheInfo(userInfo);

      logger.debug("GetUserById fetched from mailbox: {}", userId);
      return Optional.of(userInfo);

    } catch (TimeoutException e) {
      logger.error("GetUserById timed out after {}s for userId {}", MAILBOX_TIMEOUT_SECONDS, userId);
      return Optional.empty();
    } catch (MailboxServerException e) {
      logger.error("GetUserById server error for userId {}", userId, e);
      return Optional.empty();
    } catch (MailboxClientException e) {
      logger.error("GetUserById client error for userId {}", userId, e);
      return Optional.empty();
    }
  }

  public Optional<UserInfo> getUserByEmail(String email) {
    logger.debug("GetUserByEmail requested: {}", email);

    // Caffeine cache hit
    Optional<UserInfo> cached = userInfoCache.getByEmail(email);
    if (cached.isPresent()) {
      return cached;
    }

    // Coalesce concurrent lookups for the same email
    return coalesce(inflightByEmail, email, () -> loadUserByEmail(email));
  }

  private Optional<UserInfo> loadUserByEmail(String email) {
    try {
      AccountInfo info = sendWithTimeout(() -> internalClient.getAccountByEmail(email));

      UserInfo userInfo = mapAccountInfoToUserInfo(info);
      if (userInfo.userId() == null || userInfo.email() == null) {
        logger.error("GetUserByEmail: mailbox response missing userId or email for {}", email);
        return Optional.empty();
      }
      userInfo = cacheInfo(userInfo);

      logger.debug("GetUserByEmail fetched from mailbox: {}", email);
      return Optional.of(userInfo);

    } catch (TimeoutException e) {
      logger.error("GetUserByEmail timed out after {}s for email {}", MAILBOX_TIMEOUT_SECONDS, email);
      return Optional.empty();
    } catch (MailboxServerException e) {
      logger.error("GetUserByEmail server error for email {}", email, e);
      return Optional.empty();
    } catch (MailboxClientException e) {
      logger.error("GetUserByEmail client error for email {}", email, e);
      return Optional.empty();
    }
  }

  public List<UserInfo> getUsers(List<String> userIds) {
    List<String> uniqueIds = userIds.stream().distinct().toList();
    Map<String, UserInfo> results = new HashMap<>();

    // Caffeine bulk lookup
    List<String> misses = new ArrayList<>();
    for (String userId : uniqueIds) {
      userInfoCache.getByUserId(userId).ifPresentOrElse(
          info -> results.put(userId, info),
          () -> misses.add(userId)
      );
    }

    if (!misses.isEmpty()) {
      // Try batch endpoint first; fall back to individual lookups on error
      boolean batchSucceeded = false;
      try {
        List<AccountInfo> batch = sendWithTimeout(() -> internalClient.batchGetAccountsByIds(misses));
        for (AccountInfo info : batch) {
          UserInfo userInfo = mapAccountInfoToUserInfo(info);
          if (userInfo.userId() != null) {
            userInfo = cacheInfo(userInfo);
            results.put(userInfo.userId(), userInfo);
          }
        }
        batchSucceeded = true;
      } catch (TimeoutException e) {
        logger.warn("getUsers batch timed out, falling back to individual lookups", e);
      } catch (MailboxServerException | MailboxClientException e) {
        logger.warn("getUsers batch failed, falling back to individual lookups", e);
      }

      if (!batchSucceeded) {
        // Individual fallback: parallel
        List<CompletableFuture<Void>> futures = misses.stream()
            .map(userId -> CompletableFuture.supplyAsync(
                    () -> getUserById(userId), executor)
                .thenAccept(opt -> opt.ifPresent(info -> {
                  synchronized (results) {
                    results.put(info.userId(), info);
                  }
                })))
            .toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
      }
    }

    // Return in original order, skipping not-found
    return uniqueIds.stream()
        .filter(results::containsKey)
        .map(results::get)
        .toList();
  }

  // -- Cache helpers --

  private UserInfo cacheInfo(UserInfo userInfo) {
    if (!userInfoCache.isCacheEnabled()) {
      return userInfo;
    }
    userInfoCache.put(userInfo);
    return userInfo;
  }

  private UserMyself cacheMyself(
      String userId, String token, UserMyself myself, long expiresAt) {
    if (!userMyselfCache.isCacheEnabled()) {
      return myself;
    }
    userMyselfCache.put(token, userId, myself, expiresAt);
    return myself;
  }

  // -- Mapping methods --

  UserInfo mapAccountInfoToUserInfo(AccountInfo info) {
    return new UserInfo(
        info.id(),
        info.name(),
        info.displayName() != null ? info.displayName() : "",
        info.domain() != null ? info.domain() : "",
        info.status() != null ? info.status().name().toUpperCase() : "ACTIVE",
        info.isExternal() ? "GUEST" : "INTERNAL"
    );
  }

  UserMyself mapAccountInfoToUserMyself(AccountInfo info) {
    List<String> features = info.features() != null
        ? info.features().entrySet().stream()
            .filter(Map.Entry::getValue)
            .map(Map.Entry::getKey)
            .toList()
        : List.of();

    return new UserMyself(
        info.id(),
        info.name(),
        info.displayName() != null ? info.displayName() : "",
        info.domain() != null ? info.domain() : "",
        info.status() != null ? info.status().name().toUpperCase() : "ACTIVE",
        info.isExternal() ? "GUEST" : "INTERNAL",
        info.locale() != null ? info.locale() : Locale.ENGLISH.toString(),
        features,
        info.capabilities() != null ? info.capabilities() : Map.of()
    );
  }

  private static final long COALESCE_TIMEOUT_SECONDS = 5;
  private static final long MAILBOX_TIMEOUT_SECONDS = 60;

  @FunctionalInterface
  interface MailboxCall<T> {
    T call() throws MailboxServerException, MailboxClientException;
  }

  /**
   * Deduplicates concurrent lookups for the same key. Secondary threads wait up to
   * {@link #COALESCE_TIMEOUT_SECONDS} seconds before giving up.
   */
  private <T> T coalesce(
      ConcurrentHashMap<String, CompletableFuture<T>> inflight,
      String key,
      Supplier<T> loader) {
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

  /**
   * Executes a mailbox call with a timeout of {@link #MAILBOX_TIMEOUT_SECONDS} seconds.
   */
  private <R> R sendWithTimeout(MailboxCall<R> call)
      throws MailboxClientException, MailboxServerException, TimeoutException {
    try {
      return CompletableFuture.supplyAsync(() -> {
        try {
          return call.call();
        } catch (MailboxClientException | MailboxServerException e) {
          throw new CompletionException(e);
        }
      }, executor).get(MAILBOX_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (java.util.concurrent.ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof CompletionException ce) cause = ce.getCause();
      if (cause instanceof MailboxClientException mce) throw mce;
      if (cause instanceof MailboxServerException mse) throw mse;
      throw new RuntimeException(cause);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
  }
}
