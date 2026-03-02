// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.service;

import com.zextras.carbonio.user_management.cache.UserDetailsCache;
import com.zextras.carbonio.user_management.cache.UserInfoCache;
import com.zextras.carbonio.user_management.cache.record.UserDetails;
import com.zextras.carbonio.user_management.cache.record.UserInfo;
import com.zextras.carbonio.user_management.cache.repository.UserDetailsCacheRepository;
import com.zextras.carbonio.user_management.cache.repository.UserDetailsCacheRepository.CachedUserDetails;
import com.zextras.carbonio.user_management.cache.repository.UserDetailsCacheRepository.TokenLookupResult;
import com.zextras.carbonio.user_management.cache.repository.UserInfoCacheRepository;
import com.zextras.mailbox.client.MailboxClientException;
import com.zextras.mailbox.client.MailboxServerException;
import com.zextras.mailbox.client.requests.Request;
import com.zextras.mailbox.client.service.InfoRequests.Sections;
import com.zextras.mailbox.client.service.ServiceClient;
import com.zextras.wsdl.zimbraservice.ZcsPortType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.xml.ws.WebServiceException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zimbra.NamedValue;
import zimbraaccount.Attr;
import zimbraaccount.GetAccountInfoResponse;
import zimbraaccount.GetInfoResponse;
import zimbraaccount.Pref;

import static com.zextras.carbonio.user_management.UserManagementServiceConfig.FeatureFlags;
import static com.zextras.carbonio.user_management.UserManagementServiceConfig.ZimbraAttributes;
import static com.zextras.carbonio.user_management.UserManagementServiceConfig.ZimbraPreferences;
import static com.zextras.mailbox.client.service.ServiceRequests.AccountInfo;
import static com.zextras.mailbox.client.service.ServiceRequests.Info;

@ApplicationScoped
public class UserService {

  private static final Logger logger = LoggerFactory.getLogger(UserService.class);

  private final ServiceClient mailboxClient;
  private final UserInfoCache userInfoCache;
  private final UserDetailsCache userDetailsCache;
  private final UserInfoCacheRepository userInfoCacheRepo;
  private final UserDetailsCacheRepository userDetailsCacheRepo;

  // Coalescing maps: prevent concurrent L1+SOAP calls for the same key.
  // First thread to miss L2 creates a future and starts loading; subsequent threads
  // for the same key wait on the existing future instead of duplicating work.
  private final ConcurrentHashMap<String, CompletableFuture<Optional<UserInfo>>>
      inflightById = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, CompletableFuture<Optional<UserInfo>>>
      inflightByEmail = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, CompletableFuture<Optional<MyselfResult>>>
      inflightMyself = new ConcurrentHashMap<>();

  @Inject
  public UserService(
      ServiceClient mailboxClient,
      UserInfoCache userInfoCache,
      UserDetailsCache userDetailsCache,
      UserInfoCacheRepository userInfoCacheRepo,
      UserDetailsCacheRepository userDetailsCacheRepo
  ) {
    this.mailboxClient = mailboxClient;
    this.userInfoCache = userInfoCache;
    this.userDetailsCache = userDetailsCache;
    this.userInfoCacheRepo = userInfoCacheRepo;
    this.userDetailsCacheRepo = userDetailsCacheRepo;
  }

  public Optional<MyselfResult> getUserMyself(String token) {
    logger.debug("GetUserMyself requested");

    // -- L2: Caffeine (token is the primary key) --
    Optional<UserDetails> details = userDetailsCache.getByToken(token);
    Optional<String> resolvedUserId = userDetailsCache.resolveUserId(token);

    if (details.isPresent() && resolvedUserId.isPresent()) {
      Optional<UserInfo> info = userInfoCache.getByUserId(resolvedUserId.get());
      if (info.isPresent()) {
        logger.debug("GetUserMyself full L2 cache hit for userId {}", resolvedUserId.get());
        return Optional.of(new MyselfResult(info.get(), details.get()));
      }
    }

    // Coalesce concurrent L1+SOAP lookups for the same token
    return coalesce(inflightMyself, token, () -> loadUserMyself(token, resolvedUserId));
  }

  private Optional<MyselfResult> loadUserMyself(String token, Optional<String> resolvedUserId) {
    // -- L1: PostgreSQL --
    try {
      String userId = resolvedUserId.orElse(null);
      Optional<UserDetails> dbDetails = Optional.empty();

      // Resolve userId + details from DB via token
      if (userId == null) {
        Optional<TokenLookupResult> tokenResult = userDetailsCacheRepo.findByToken(token);
        if (tokenResult.isPresent()) {
          var result = tokenResult.get();
          userId = result.userId();
          dbDetails = Optional.of(result.details());
          userDetailsCache.put(token, userId, result.details(), result.expiresAt());
        }
      }

      if (userId != null) {
        String resolvedId = userId;

        // Resolve info: L2 → L1
        Optional<UserInfo> info = userInfoCache.getByUserId(resolvedId);
        if (info.isEmpty()) {
          info = userInfoCacheRepo.findByUserId(resolvedId);
          info.ifPresent(userInfoCache::put);
        }

        // Resolve details: already from token lookup or L1
        if (dbDetails.isEmpty()) {
          dbDetails = userDetailsCacheRepo.findByUserId(resolvedId).map(cached -> {
            userDetailsCache.put(token, resolvedId, cached.details(), cached.expiresAt());
            return cached.details();
          });
        }

        if (info.isPresent() && dbDetails.isPresent()) {
          logger.debug("GetUserMyself L1 cache hit for userId {}", resolvedId);
          return Optional.of(new MyselfResult(info.get(), dbDetails.get()));
        }
      }
    } catch (Exception e) {
      logger.warn("GetUserMyself L1 cache lookup failed", e);
    }

    // -- SOAP fallback --
    // Read config before SOAP: fail fast if cache.userinfo-ttl is missing
    userInfoCache.readTtlSeconds();
    try {
      Request<ZcsPortType, GetInfoResponse> request =
          Info.sections(Sections.children, Sections.attrs, Sections.prefs).withAuthToken(token);
      GetInfoResponse response = mailboxClient.send(request);

      UserInfo userInfo = mapGetInfoToUserInfo(response);
      if (userInfo.userId() == null || userInfo.email() == null) {
        logger.error("GetUserMyself: mailbox response missing userId or email");
        return Optional.empty();
      }
      UserDetails details = mapGetInfoToUserDetails(response);
      long expiresAt = userDetailsCache.computeExpiresAt(response.getLifetime());

      userInfo = persistAndCacheInfo(userInfo);
      details = persistAndCacheDetails(userInfo.userId(), token, details, expiresAt);

      logger.debug("GetUserMyself fetched from mailbox for userId {}", userInfo.userId());
      return Optional.of(new MyselfResult(userInfo, details));

    } catch (WebServiceException | MailboxServerException e) {
      logger.error("GetUserMyself server error", e);
      return Optional.empty();
    } catch (MailboxClientException e) {
      logger.error("GetUserMyself client error", e);
      return Optional.empty();
    }
  }

  public Optional<UserInfo> getUserById(String userId, String callerToken) {
    logger.debug("GetUserById requested: {}", userId);

    // L2: Caffeine
    Optional<UserInfo> cached = userInfoCache.getByUserId(userId);
    if (cached.isPresent()) {
      return cached;
    }

    // Coalesce concurrent L1+SOAP lookups for the same userId
    return coalesce(inflightById, userId, () -> loadUserById(userId, callerToken));
  }

  private Optional<UserInfo> loadUserById(String userId, String callerToken) {
    // L1: PostgreSQL
    try {
      Optional<UserInfo> cached = userInfoCacheRepo.findByUserId(userId);
      if (cached.isPresent()) {
        logger.debug("GetUserById L1 cache hit: {}", userId);
        userInfoCache.put(cached.get());
        return cached;
      }
    } catch (Exception e) {
      logger.warn("GetUserById L1 cache lookup failed for userId {}", userId, e);
    }

    // Read config before SOAP: fail fast if cache.userinfo-ttl is missing
    userInfoCache.readTtlSeconds();
    try {
      Request<ZcsPortType, GetAccountInfoResponse> request =
          AccountInfo.byId(userId).withAuthToken(callerToken);
      GetAccountInfoResponse response = mailboxClient.send(request);

      UserInfo userInfo = mapGetAccountInfoToUserInfo(response);
      if (userInfo.userId() == null || userInfo.email() == null) {
        logger.error("GetUserById: mailbox response missing userId or email for {}", userId);
        return Optional.empty();
      }
      userInfo = persistAndCacheInfo(userInfo);

      logger.debug("GetUserById fetched from mailbox: {}", userId);
      return Optional.of(userInfo);

    } catch (WebServiceException | MailboxServerException e) {
      logger.error("GetUserById server error for userId {}", userId, e);
      return Optional.empty();
    } catch (MailboxClientException e) {
      logger.error("GetUserById client error for userId {}", userId, e);
      return Optional.empty();
    }
  }

  public Optional<UserInfo> getUserByEmail(String email, String callerToken) {
    logger.debug("GetUserByEmail requested: {}", email);

    // L2: Caffeine
    Optional<UserInfo> cached = userInfoCache.getByEmail(email);
    if (cached.isPresent()) {
      return cached;
    }

    // Coalesce concurrent L1+SOAP lookups for the same email
    return coalesce(inflightByEmail, email, () -> loadUserByEmail(email, callerToken));
  }

  private Optional<UserInfo> loadUserByEmail(String email, String callerToken) {
    // L1: PostgreSQL
    try {
      Optional<UserInfo> cached = userInfoCacheRepo.findByEmail(email);
      if (cached.isPresent()) {
        logger.debug("GetUserByEmail L1 cache hit: {}", email);
        userInfoCache.put(cached.get());
        return cached;
      }
    } catch (Exception e) {
      logger.warn("GetUserByEmail L1 cache lookup failed for email {}", email, e);
    }

    // Read config before SOAP: fail fast if cache.userinfo-ttl is missing
    userInfoCache.readTtlSeconds();
    try {
      Request<ZcsPortType, GetAccountInfoResponse> request =
          AccountInfo.byEmail(email).withAuthToken(callerToken);
      GetAccountInfoResponse response = mailboxClient.send(request);

      UserInfo userInfo = mapGetAccountInfoToUserInfo(response);
      if (userInfo.userId() == null || userInfo.email() == null) {
        logger.error("GetUserByEmail: mailbox response missing userId or email for {}", email);
        return Optional.empty();
      }
      userInfo = persistAndCacheInfo(userInfo);

      logger.debug("GetUserByEmail fetched from mailbox: {}", email);
      return Optional.of(userInfo);

    } catch (WebServiceException | MailboxServerException e) {
      logger.error("GetUserByEmail server error for email {}", email, e);
      return Optional.empty();
    } catch (MailboxClientException e) {
      logger.error("GetUserByEmail client error for email {}", email, e);
      return Optional.empty();
    }
  }

  public List<UserInfo> getUsers(List<String> userIds, String callerToken) {
    List<String> uniqueIds = userIds.stream().distinct().toList();
    Map<String, UserInfo> results = new HashMap<>();

    // L2: Caffeine bulk lookup
    List<String> l2Misses = new ArrayList<>();
    for (String userId : uniqueIds) {
      userInfoCache.getByUserId(userId).ifPresentOrElse(
          info -> results.put(userId, info),
          () -> l2Misses.add(userId)
      );
    }

    if (!l2Misses.isEmpty()) {
      // L1: PostgreSQL single batch query for all L2 misses
      Set<String> soapNeeded = new LinkedHashSet<>(l2Misses);
      try {
        List<UserInfo> l1Results = userInfoCacheRepo.findByUserIds(l2Misses);
        for (UserInfo info : l1Results) {
          results.put(info.userId(), info);
          userInfoCache.put(info);
          soapNeeded.remove(info.userId());
        }
      } catch (Exception e) {
        logger.warn("getUsers L1 batch lookup failed, falling back to SOAP for all misses", e);
      }

      // SOAP: sequential for remaining misses (can't batch SOAP calls)
      for (String userId : soapNeeded) {
        getUserById(userId, callerToken).ifPresent(info -> results.put(userId, info));
      }
    }

    // Return in original order, skipping not-found
    return uniqueIds.stream()
        .filter(results::containsKey)
        .map(results::get)
        .toList();
  }

  // -- Persist & cache helpers --

  private UserInfo persistAndCacheInfo(UserInfo userInfo) {
    try {
      long expiresAt = userInfoCache.computeExpiresAt();
      userInfo = userInfoCacheRepo.upsert(userInfo, expiresAt);
    } catch (Exception e) {
      logger.warn("Failed to persist user info to L1 cache for userId {}", userInfo.userId(), e);
    }
    userInfoCache.put(userInfo);
    return userInfo;
  }

  private UserDetails persistAndCacheDetails(
      String userId, String token, UserDetails details, long expiresAt) {
    try {
      details = userDetailsCacheRepo.upsert(userId, token, details, expiresAt);
    } catch (Exception e) {
      logger.warn("Failed to persist user details to L1 cache for userId {}", userId, e);
    }
    userDetailsCache.put(token, userId, details, expiresAt);
    return details;
  }

  // -- Mapping methods --

  UserInfo mapGetInfoToUserInfo(GetInfoResponse response) {
    List<Attr> attrs = response.getAttrs() != null ? response.getAttrs().getAttr() : List.of();
    return mapAttributesToUserInfo(
        response.getName(), response.getPublicURL(),
        attrs, Attr::getName, Attr::getValue);
  }

  private static final Set<String> FEATURE_FLAGS = Set.of(
      FeatureFlags.FILES_ENABLED,
      FeatureFlags.WSC_ENABLED,
      FeatureFlags.TASKS_ENABLED
  );

  UserDetails mapGetInfoToUserDetails(GetInfoResponse response) {
    String locale = Locale.ENGLISH.toString();
    Map<String, Boolean> featureList = new HashMap<>();

    if (response.getAttrs() != null) {
      for (Attr attr : response.getAttrs().getAttr()) {
        if (FEATURE_FLAGS.contains(attr.getName())) {
          featureList.put(attr.getName(), "TRUE".equalsIgnoreCase(attr.getValue()));
        }
      }
    }

    if (response.getPrefs() != null) {
      for (Pref pref : response.getPrefs().getPref()) {
        if (ZimbraPreferences.LOCALE.equals(pref.getName())) {
          Locale parsed = Locale.forLanguageTag(pref.getValue().replace('_', '-'));
          if (parsed.getLanguage().isEmpty()) {
            logger.warn("Invalid locale format '{}', falling back to '{}'",
                pref.getValue(), Locale.ENGLISH);
          } else {
            locale = parsed.toString();
          }
          break;
        }
      }
    }

    return new UserDetails(locale, featureList);
  }

  UserInfo mapGetAccountInfoToUserInfo(GetAccountInfoResponse response) {
    return mapAttributesToUserInfo(
        response.getName(), response.getPublicURL(),
        response.getAttr(), NamedValue::getName, NamedValue::getValue);
  }

  private <T> UserInfo mapAttributesToUserInfo(
      String email, String domain,
      List<T> attrs, Function<T, String> getName, Function<T, String> getValue) {
    String userId = null;
    String fullName = "";
    String status = "ACTIVE";
    String type = "INTERNAL";

    for (T attr : attrs) {
      switch (getName.apply(attr)) {
        case ZimbraAttributes.DISPLAY_NAME -> fullName = getValue.apply(attr);
        case ZimbraAttributes.ID -> userId = getValue.apply(attr);
        case ZimbraAttributes.ACCOUNT_STATUS -> status = getValue.apply(attr).toUpperCase();
        case ZimbraAttributes.IS_EXTERNAL_VIRTUAL_ACCOUNT ->
            type = Boolean.parseBoolean(getValue.apply(attr).toLowerCase()) ? "GUEST" : "INTERNAL";
      }
    }

    return new UserInfo(userId, email, fullName, domain, status, type);
  }

  /**
   * Deduplicates concurrent lookups for the same key. The first thread to call this for a given
   * key executes the supplier; subsequent threads for the same key wait for the first thread's
   * result instead of duplicating work (L1 queries, SOAP calls).
   */
  private <T> T coalesce(
      ConcurrentHashMap<String, CompletableFuture<T>> inflight,
      String key,
      Supplier<T> loader) {
    CompletableFuture<T> future = new CompletableFuture<>();
    CompletableFuture<T> existing = inflight.putIfAbsent(key, future);
    if (existing != null) {
      return existing.join();
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

  public record MyselfResult(UserInfo info, UserDetails details) {}
}
