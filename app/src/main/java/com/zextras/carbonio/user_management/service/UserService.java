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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    // -- L2: Caffeine --
    Optional<String> resolvedUserId = userDetailsCache.resolveUserId(token);
    if (resolvedUserId.isPresent()) {
      Optional<UserInfo> info = userInfoCache.getByUserId(resolvedUserId.get());
      Optional<UserDetails> details = userDetailsCache.getByUserId(resolvedUserId.get());

      if (info.isPresent() && details.isPresent()) {
        logger.debug("GetUserMyself full L2 cache hit for userId {}", resolvedUserId.get());
        return Optional.of(new MyselfResult(info.get(), details.get()));
      }
    }

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
          long remainingMs = result.expiresAt() - System.currentTimeMillis();
          userDetailsCache.put(userId, token, result.details(), remainingMs);
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

        // Resolve details: already from token lookup or L2 → L1
        if (dbDetails.isEmpty()) {
          dbDetails = userDetailsCache.getByUserId(resolvedId);
        }
        if (dbDetails.isEmpty()) {
          dbDetails = userDetailsCacheRepo.findByUserId(resolvedId).map(cached -> {
            long remainingMs = cached.expiresAt() - System.currentTimeMillis();
            userDetailsCache.put(resolvedId, token, cached.details(), remainingMs);
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
    try {
      Request<ZcsPortType, GetInfoResponse> request =
          Info.sections(Sections.children, Sections.attrs, Sections.prefs).withAuthToken(token);
      GetInfoResponse response = mailboxClient.send(request);

      UserInfo userInfo = mapGetInfoToUserInfo(response);
      UserDetails details = mapGetInfoToUserDetails(response);
      long remainingMs = response.getLifetime();

      userInfo = persistAndCacheInfo(userInfo);
      details = persistAndCacheDetails(userInfo.userId(), token, details, remainingMs);

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

    // L1: PostgreSQL
    try {
      cached = userInfoCacheRepo.findByUserId(userId);
      if (cached.isPresent()) {
        logger.debug("GetUserById L1 cache hit: {}", userId);
        userInfoCache.put(cached.get());
        return cached;
      }
    } catch (Exception e) {
      logger.warn("GetUserById L1 cache lookup failed for userId {}", userId, e);
    }

    // SOAP
    try {
      Request<ZcsPortType, GetAccountInfoResponse> request =
          AccountInfo.byId(userId).withAuthToken(callerToken);
      GetAccountInfoResponse response = mailboxClient.send(request);

      UserInfo userInfo = persistAndCacheInfo(mapGetAccountInfoToUserInfo(response));

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

    // L1: PostgreSQL
    try {
      cached = userInfoCacheRepo.findByEmail(email);
      if (cached.isPresent()) {
        logger.debug("GetUserByEmail L1 cache hit: {}", email);
        userInfoCache.put(cached.get());
        return cached;
      }
    } catch (Exception e) {
      logger.warn("GetUserByEmail L1 cache lookup failed for email {}", email, e);
    }

    // SOAP
    try {
      Request<ZcsPortType, GetAccountInfoResponse> request =
          AccountInfo.byEmail(email).withAuthToken(callerToken);
      GetAccountInfoResponse response = mailboxClient.send(request);

      UserInfo userInfo = persistAndCacheInfo(mapGetAccountInfoToUserInfo(response));

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
    return userIds.stream()
        .distinct()
        .flatMap(userId -> getUserById(userId, callerToken).stream())
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
      String userId, String token, UserDetails details, long remainingMs) {
    try {
      long expiresAt = userDetailsCache.computeExpiresAt(remainingMs);
      details = userDetailsCacheRepo.upsert(userId, token, details, expiresAt);
    } catch (Exception e) {
      logger.warn("Failed to persist user details to L1 cache for userId {}", userId, e);
    }
    userDetailsCache.put(userId, token, details, remainingMs);
    return details;
  }

  // -- Mapping methods --

  UserInfo mapGetInfoToUserInfo(GetInfoResponse response) {
    String userId = null;
    String email = response.getName();
    String fullName = "";
    String domain = response.getPublicURL();
    String status = "ACTIVE";
    String type = "INTERNAL";

    if (response.getAttrs() != null) {
      for (Attr attr : response.getAttrs().getAttr()) {
        switch (attr.getName()) {
          case ZimbraAttributes.DISPLAY_NAME -> fullName = attr.getValue();
          case ZimbraAttributes.ID -> userId = attr.getValue();
          case ZimbraAttributes.ACCOUNT_STATUS -> status = attr.getValue().toUpperCase();
          case ZimbraAttributes.IS_EXTERNAL_VIRTUAL_ACCOUNT ->
              type = Boolean.parseBoolean(attr.getValue().toLowerCase()) ? "GUEST" : "INTERNAL";
        }
      }
    }

    return new UserInfo(userId, email, fullName, domain, status, type);
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
          try {
            locale = Locale.forLanguageTag(pref.getValue().replace('_', '-')).toString();
          } catch (IllegalArgumentException e) {
            logger.error("Invalid locale format '{}', falling back to '{}'",
                pref.getValue(), Locale.ENGLISH);
            locale = Locale.ENGLISH.toString();
          }
          break;
        }
      }
    }

    return new UserDetails(locale, featureList);
  }

  UserInfo mapGetAccountInfoToUserInfo(GetAccountInfoResponse response) {
    String userId = null;
    String email = response.getName();
    String fullName = "";
    String domain = response.getPublicURL();
    String status = "ACTIVE";
    String type = "INTERNAL";

    for (NamedValue attr : response.getAttr()) {
      switch (attr.getName()) {
        case ZimbraAttributes.DISPLAY_NAME -> fullName = attr.getValue();
        case ZimbraAttributes.ID -> userId = attr.getValue();
        case ZimbraAttributes.ACCOUNT_STATUS -> status = attr.getValue().toUpperCase();
        case ZimbraAttributes.IS_EXTERNAL_VIRTUAL_ACCOUNT ->
            type = Boolean.parseBoolean(attr.getValue().toLowerCase()) ? "GUEST" : "INTERNAL";
      }
    }

    return new UserInfo(userId, email, fullName, domain, status, type);
  }

  public record MyselfResult(UserInfo info, UserDetails details) {}
}
