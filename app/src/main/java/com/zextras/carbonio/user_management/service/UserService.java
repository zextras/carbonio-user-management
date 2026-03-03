// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.service;

import com.zextras.carbonio.user_management.cache.UserInfoCache;
import com.zextras.carbonio.user_management.cache.UserMyselfCache;
import com.zextras.carbonio.user_management.record.UserInfo;
import com.zextras.carbonio.user_management.record.UserMyself;
import com.zextras.carbonio.user_management.repository.UserInfoCacheRepository;
import com.zextras.carbonio.user_management.repository.UserMyselfCacheRepository;
import com.zextras.carbonio.user_management.repository.UserMyselfCacheRepository.TokenLookupResult;
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
  private final UserMyselfCache userMyselfCache;
  private final UserInfoCacheRepository userInfoCacheRepo;
  private final UserMyselfCacheRepository userMyselfCacheRepo;

  // Coalescing maps: prevent concurrent L1+SOAP calls for the same key.
  private final ConcurrentHashMap<String, CompletableFuture<Optional<UserInfo>>>
      inflightById = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, CompletableFuture<Optional<UserInfo>>>
      inflightByEmail = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, CompletableFuture<Optional<UserMyself>>>
      inflightMyself = new ConcurrentHashMap<>();

  @Inject
  public UserService(
      ServiceClient mailboxClient,
      UserInfoCache userInfoCache,
      UserMyselfCache userMyselfCache,
      UserInfoCacheRepository userInfoCacheRepo,
      UserMyselfCacheRepository userMyselfCacheRepo
  ) {
    this.mailboxClient = mailboxClient;
    this.userInfoCache = userInfoCache;
    this.userMyselfCache = userMyselfCache;
    this.userInfoCacheRepo = userInfoCacheRepo;
    this.userMyselfCacheRepo = userMyselfCacheRepo;
  }

  public Optional<UserMyself> getUserMyself(String token) {
    logger.debug("GetUserMyself requested");

    // -- L2: Caffeine (token is the primary key) --
    Optional<UserMyself> cached = userMyselfCache.getByToken(token);
    if (cached.isPresent()) {
      logger.debug("GetUserMyself L2 cache hit");
      safeWarmUserInfoCache(cached.get());
      return cached;
    }

    // Coalesce concurrent L1+SOAP lookups for the same token
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
      return;  // L2 hit
    }
    try {
      if (userInfoCacheRepo.findByUserId(userId).isPresent()) {
        return;  // L1 hit
      }
    } catch (Exception e) {
      logger.warn("L1 read failed during warming for userId={}, treating as miss", userId, e);
    }
    UserInfo userInfo = new UserInfo(
        myself.userId(), myself.email(), myself.fullName(),
        myself.domain(), myself.status(), myself.type());
    persistAndCacheInfo(userInfo);
  }

  private Optional<UserMyself> loadUserMyself(String token) {
    // -- L1: PostgreSQL (best-effort) --
    try {
      Optional<TokenLookupResult> tokenResult = userMyselfCacheRepo.findByToken(token);
      if (tokenResult.isPresent()) {
        var result = tokenResult.get();
        userMyselfCache.put(token, result.userId(), result.myself(), result.expiresAt());
        logger.debug("GetUserMyself L1 cache hit for userId {}", result.userId());
        return Optional.of(result.myself());
      }
    } catch (Exception e) {
      logger.warn("L1 read failed for UserMyself token, falling back to SOAP", e);
    }

    // -- SOAP fallback --
    try {
      Request<ZcsPortType, GetInfoResponse> request =
          Info.sections(Sections.children, Sections.attrs, Sections.prefs).withAuthToken(token);
      GetInfoResponse response = mailboxClient.send(request);

      UserMyself myself = mapGetInfoToUserMyself(response);
      if (myself.userId() == null || myself.email() == null) {
        logger.error("GetUserMyself: mailbox response missing userId or email");
        return Optional.empty();
      }
      long expiresAt = userMyselfCache.computeExpiresAt(response.getLifetime());

      myself = persistAndCacheMyself(myself.userId(), token, myself, expiresAt);

      logger.debug("GetUserMyself fetched from mailbox for userId {}", myself.userId());
      return Optional.of(myself);

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
    // L1: PostgreSQL (best-effort)
    try {
      Optional<UserInfo> cached = userInfoCacheRepo.findByUserId(userId);
      if (cached.isPresent()) {
        logger.debug("GetUserById L1 cache hit: {}", userId);
        userInfoCache.put(cached.get());
        return cached;
      }
    } catch (Exception e) {
      logger.warn("L1 read failed for UserInfo userId={}, falling back to SOAP", userId, e);
    }

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
    // L1: PostgreSQL (best-effort)
    try {
      Optional<UserInfo> cached = userInfoCacheRepo.findByEmail(email);
      if (cached.isPresent()) {
        logger.debug("GetUserByEmail L1 cache hit: {}", email);
        userInfoCache.put(cached.get());
        return cached;
      }
    } catch (Exception e) {
      logger.warn("L1 read failed for UserInfo email={}, falling back to SOAP", email, e);
    }

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
      // L1: PostgreSQL single batch query for all L2 misses (best-effort)
      Set<String> soapNeeded = new LinkedHashSet<>(l2Misses);
      try {
        List<UserInfo> l1Results = userInfoCacheRepo.findByUserIds(l2Misses);
        for (UserInfo info : l1Results) {
          results.put(info.userId(), info);
          userInfoCache.put(info);
          soapNeeded.remove(info.userId());
        }
      } catch (Exception e) {
        logger.warn("L1 batch read failed for getUsers, all misses will go to SOAP", e);
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
    if (!userInfoCache.isCacheEnabled()) {
      return userInfo;
    }
    long expiresAt = userInfoCache.computeExpiresAt();
    try {
      userInfo = userInfoCacheRepo.upsert(userInfo, expiresAt);
    } catch (Exception e) {
      logger.warn("L1 write failed for UserInfo userId={}, continuing with L2 only",
          userInfo.userId(), e);
    }
    userInfoCache.put(userInfo);
    return userInfo;
  }

  private UserMyself persistAndCacheMyself(
      String userId, String token, UserMyself myself, long expiresAt) {
    if (!userMyselfCache.isCacheEnabled()) {
      return myself;
    }
    try {
      myself = userMyselfCacheRepo.upsert(userId, token, myself, expiresAt);
    } catch (Exception e) {
      logger.warn("L1 write failed for UserMyself userId={}, continuing with L2 only",
          userId, e);
    }
    userMyselfCache.put(token, userId, myself, expiresAt);
    return myself;
  }

  // -- Mapping methods --

  private static final Set<String> FEATURE_FLAGS = Set.of(
      FeatureFlags.FILES_ENABLED,
      FeatureFlags.WSC_ENABLED,
      FeatureFlags.TASKS_ENABLED
  );

  UserMyself mapGetInfoToUserMyself(GetInfoResponse response) {
    List<Attr> attrs = response.getAttrs() != null ? response.getAttrs().getAttr() : List.of();

    String userId = null;
    String fullName = "";
    String status = "ACTIVE";
    String type = "INTERNAL";
    String locale = Locale.ENGLISH.toString();
    Map<String, Boolean> featureList = new HashMap<>();

    for (Attr attr : attrs) {
      switch (attr.getName()) {
        case ZimbraAttributes.DISPLAY_NAME -> fullName = attr.getValue();
        case ZimbraAttributes.ID -> userId = attr.getValue();
        case ZimbraAttributes.ACCOUNT_STATUS -> status = attr.getValue().toUpperCase();
        case ZimbraAttributes.IS_EXTERNAL_VIRTUAL_ACCOUNT ->
            type = Boolean.parseBoolean(attr.getValue().toLowerCase()) ? "GUEST" : "INTERNAL";
        default -> {
          if (FEATURE_FLAGS.contains(attr.getName())) {
            featureList.put(attr.getName(), "TRUE".equalsIgnoreCase(attr.getValue()));
          }
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

    return new UserMyself(
        userId, response.getName(), fullName, response.getPublicURL(),
        status, type, locale, featureList);
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
   * Deduplicates concurrent lookups for the same key.
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
}
