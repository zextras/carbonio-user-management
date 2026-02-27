// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.service;

import com.zextras.carbonio.user_management.cache.UserDetailsCache;
import com.zextras.carbonio.user_management.cache.UserInfoCache;
import com.zextras.carbonio.user_management.cache.record.UserDetails;
import com.zextras.carbonio.user_management.cache.record.UserInfo;
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
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zimbra.NamedValue;
import zimbraaccount.Attr;
import zimbraaccount.GetAccountInfoResponse;
import zimbraaccount.GetInfoResponse;
import zimbraaccount.Pref;

import static com.zextras.mailbox.client.service.ServiceRequests.AccountInfo;
import static com.zextras.mailbox.client.service.ServiceRequests.Info;

@ApplicationScoped
public class UserService {

  private static final Logger logger = LoggerFactory.getLogger(UserService.class);

  private final ServiceClient mailboxClient;
  private final UserInfoCache userInfoCache;
  private final UserDetailsCache userDetailsCache;

  @Inject
  public UserService(
      ServiceClient mailboxClient,
      UserInfoCache userInfoCache,
      UserDetailsCache userDetailsCache
  ) {
    this.mailboxClient = mailboxClient;
    this.userInfoCache = userInfoCache;
    this.userDetailsCache = userDetailsCache;
  }

  public Optional<MyselfResult> getUserMyself(String token) {
    logger.debug("GetUserMyself requested");

    Optional<String> resolvedUserId = userDetailsCache.resolveUserId(token);

    if (resolvedUserId.isPresent()) {
      String userId = resolvedUserId.get();
      Optional<UserInfo> cachedInfo = userInfoCache.getByUserId(userId);
      Optional<UserDetails> cachedDetails = userDetailsCache.getByUserId(userId);

      if (cachedInfo.isPresent() && cachedDetails.isPresent()) {
        logger.debug("GetUserMyself full cache hit for userId {}", userId);
        return Optional.of(new MyselfResult(cachedInfo.get(), cachedDetails.get()));
      }
    }

    try {
      Request<ZcsPortType, GetInfoResponse> request =
          Info.sections(Sections.children, Sections.attrs, Sections.prefs).withAuthToken(token);
      GetInfoResponse response = mailboxClient.send(request);

      UserInfo userInfo = mapGetInfoToUserInfo(response);
      UserDetails details = mapGetInfoToUserDetails(response);
      long remainingMs = response.getLifetime();

      userInfoCache.put(userInfo);
      userDetailsCache.put(userInfo.userId(), token, details, remainingMs);

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

    Optional<UserInfo> cached = userInfoCache.getByUserId(userId);
    if (cached.isPresent()) {
      return cached;
    }

    try {
      Request<ZcsPortType, GetAccountInfoResponse> request =
          AccountInfo.byId(userId).withAuthToken(callerToken);
      GetAccountInfoResponse response = mailboxClient.send(request);

      UserInfo userInfo = mapGetAccountInfoToUserInfo(response);
      userInfoCache.put(userInfo);

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

    Optional<UserInfo> cached = userInfoCache.getByEmail(email);
    if (cached.isPresent()) {
      return cached;
    }

    try {
      Request<ZcsPortType, GetAccountInfoResponse> request =
          AccountInfo.byEmail(email).withAuthToken(callerToken);
      GetAccountInfoResponse response = mailboxClient.send(request);

      UserInfo userInfo = mapGetAccountInfoToUserInfo(response);
      userInfoCache.put(userInfo);

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
        .map(userId -> getUserById(userId, callerToken).orElse(null))
        .filter(Objects::nonNull)
        .toList();
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
          case "displayName" -> fullName = attr.getValue();
          case "zimbraId" -> userId = attr.getValue();
          case "zimbraAccountStatus" -> status = attr.getValue().toUpperCase();
          case "zimbraIsExternalVirtualAccount" ->
              type = Boolean.parseBoolean(attr.getValue().toLowerCase()) ? "GUEST" : "INTERNAL";
        }
      }
    }

    return new UserInfo(userId, email, fullName, domain, status, type);
  }

  UserDetails mapGetInfoToUserDetails(GetInfoResponse response) {
    String locale = Locale.ENGLISH.toString();
    Map<String, String> carbonioAttributes = new HashMap<>();

    if (response.getAttrs() != null) {
      for (Attr attr : response.getAttrs().getAttr()) {
        if (attr.getName().startsWith("carbonio")) {
          carbonioAttributes.put(attr.getName(), attr.getValue());
        }
      }
    }

    if (response.getPrefs() != null) {
      for (Pref pref : response.getPrefs().getPref()) {
        if ("zimbraPrefLocale".equals(pref.getName())) {
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

    return new UserDetails(locale, carbonioAttributes);
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
        case "displayName" -> fullName = attr.getValue();
        case "zimbraId" -> userId = attr.getValue();
        case "zimbraAccountStatus" -> status = attr.getValue().toUpperCase();
        case "zimbraIsExternalVirtualAccount" ->
            type = Boolean.parseBoolean(attr.getValue().toLowerCase()) ? "GUEST" : "INTERNAL";
      }
    }

    return new UserInfo(userId, email, fullName, domain, status, type);
  }

  public record MyselfResult(UserInfo info, UserDetails details) {}
}
