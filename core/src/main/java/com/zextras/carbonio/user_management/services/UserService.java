// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.services;

import com.google.inject.Inject;
import com.zextras.carbonio.user_management.cache.CacheManager;
import com.zextras.carbonio.user_management.entities.UserToken;
import com.zextras.carbonio.user_management.generated.model.UserId;
import com.zextras.carbonio.user_management.generated.model.UserInfo;
import com.zextras.carbonio.user_management.generated.model.UserMyself;
import com.zextras.carbonio.user_management.generated.model.UserStatus;
import com.zextras.carbonio.user_management.generated.model.UserType;
import com.zextras.mailbox.client.MailboxClient;
import com.zextras.mailbox.client.MailboxClientException;
import com.zextras.mailbox.client.MailboxServerException;
import com.zextras.mailbox.client.internal.MailboxInternalApiClient;
import com.zextras.mailbox.client.requests.Request;
import com.zextras.mailbox.client.service.InfoRequests.Sections;
import com.zextras.mailbox.client.service.ServiceClient;
import com.zextras.wsdl.zimbraservice.ZcsPortType;
import jakarta.xml.ws.WebServiceException;
import org.apache.commons.lang3.LocaleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zimbra.NamedValue;
import zimbraaccount.Attr;
import zimbraaccount.GetAccountInfoResponse;
import zimbraaccount.GetInfoResponse;
import zimbraaccount.Pref;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import static com.zextras.mailbox.client.service.ServiceRequests.AccountInfo;
import static com.zextras.mailbox.client.service.ServiceRequests.Info;

public class UserService {

  private static final Logger logger = LoggerFactory.getLogger(UserService.class);

  private final CacheManager cacheManager;
  private final ServiceClient mailboxClient;
  private final MailboxInternalApiClient mailboxInternalApiClient;

  @Inject
  public UserService(CacheManager cacheManager, ServiceClient mailboxClient,
      MailboxInternalApiClient mailboxInternalApiClient) {
    this.cacheManager = cacheManager;
    this.mailboxClient = mailboxClient;
    this.mailboxInternalApiClient = mailboxInternalApiClient;
  }

  public Response getUsers(List<String> userIds, String token, Boolean ignoreCache) {
    return Response.ok()
        .entity(
            userIds.stream()
                .distinct()
                .map(
                    userId -> {
                      logger.debug("Requested: {}", userId);

                      UserInfo userInfo = null;
                      if (ignoreCache == null || !ignoreCache) {
                        userInfo = cacheManager.getUserByIdCache().getIfPresent(userId);
                      }

                      if (userInfo == null) {
                        try {
                          final Request<ZcsPortType, GetAccountInfoResponse> request =
                              AccountInfo.byId(userId).withAuthToken(token);
                          final GetAccountInfoResponse accountInfo = mailboxClient.send(request);

                          userInfo = createUserInfoFromAccountInfoResponse(accountInfo);
                          cacheManager.getUserByIdCache().put(userId, userInfo);
                          cacheManager.getUserByEmailCache().put(userInfo.getEmail(), userInfo);
                          logger.debug("Found: {}", userId);
                        } catch (WebServiceException | MailboxServerException e) {
                          logger.error("GetUsers with userId {} and token {} server failed.", userId, token, e);
                        } catch (MailboxClientException e) {
                            logger.error("GetUsers with userId {} and token {} client failed.", userId, token, e);
                        }
                      }

                      return userInfo;
                    })
                .filter(Objects::nonNull)
                .toList())
        .build();
  }

  public Optional<UserInfo> getInfoById(String userId, String token, Boolean ignoreCache) {
    logger.debug("Requested: {}", userId);

    UserInfo userInfo = null;
    if (ignoreCache == null || !ignoreCache) {
      userInfo = cacheManager.getUserByIdCache().getIfPresent(userId);
    }

    if (userInfo == null) {
      try {
        final Request<ZcsPortType, GetAccountInfoResponse> request =
            AccountInfo.byId(userId).withAuthToken(token);
        final GetAccountInfoResponse accountInfo = mailboxClient.send(request);

        userInfo = createUserInfoFromAccountInfoResponse(accountInfo);
        cacheManager.getUserByIdCache().put(userId, userInfo);
        cacheManager.getUserByEmailCache().put(userInfo.getEmail(), userInfo);

      } catch (WebServiceException | MailboxServerException e) {
        logger.error("GetInfoById with userId {} and token {} server failed.", userId, token, e);
        return Optional.empty();
      } catch (MailboxClientException e) {
        logger.error("GetInfoById with userId {} and token {} client failed.", userId, token, e);
        return Optional.empty();
      }
    }
    logger.debug(userInfo.getId().getUserId());
    return Optional.of(userInfo);
  }

  public Optional<UserInfo> getInfoByEmail(String userEmail, String token, Boolean ignoreCache) {
    logger.debug("Requested: {}", userEmail);

    UserInfo userInfo = null;
    if (ignoreCache == null || !ignoreCache) {
      userInfo = cacheManager.getUserByEmailCache().getIfPresent(userEmail);
    }

    if (userInfo == null) {
      try {
        final Request<ZcsPortType, GetAccountInfoResponse> request =
            AccountInfo.byEmail(userEmail).withAuthToken(token);
        final GetAccountInfoResponse accountInfo = mailboxClient.send(request);

        userInfo = createUserInfoFromAccountInfoResponse(accountInfo);
        cacheManager.getUserByEmailCache().put(userEmail, userInfo);
        cacheManager.getUserByIdCache().put(userInfo.getId().getUserId(), userInfo);

      } catch (WebServiceException | MailboxServerException e) {
        logger.error("GetInfoByEmail with user email {} and token {} server failed.", userEmail, token, e);
        return Optional.empty();
      } catch (MailboxClientException e) {
        logger.error("GetInfoByEmail with user email {} and token {} client failed.", userEmail, token, e);
        return Optional.empty();
      }
    }
    logger.debug(userInfo.getId().getUserId());
    return Optional.of(userInfo);
  }

  public Optional<UserMyself> getMyselfByToken(String token, Boolean ignoreCache) {
    var response = mailboxInternalApiClient.getMyAccountInfo(token);
    var userMyself = new UserMyself();
    final UserId userId = new UserId();
    userId.setUserId(response.id());
    userMyself.setId(userId);
    userMyself.setEmail(response.name());
    userMyself.setFullName(response.displayName());
    userMyself.setDomain(response.domainId());
    userMyself.setStatus(UserStatus.valueOf(response.status().name()));
    userMyself.setLocale(response.locale());
    userMyself.setType(response.isExternal() ? UserType.GUEST : UserType.INTERNAL);
    // TODO: attributes, too lazy to map boolean to string
    // TODO: handle failure, I'm just trying the APIs for now
    return Optional.of(userMyself);
  }

  public Optional<UserId> validateUserToken(String token) {
    logger.debug("Validate: {}", token);
    // We can't use Optional.ofNullable because validateAuthToken throws exceptions and
    // we need to return different status codes based on different exceptions
    UserToken userToken = cacheManager.getUserTokenCache().getIfPresent(token);

    if (userToken == null) {
      try {
        final Request<ZcsPortType, GetInfoResponse> request =
            Info.sections(Sections.children).withAuthToken(token);
        final GetInfoResponse infoResponse = mailboxClient.send(request);

        userToken = new UserToken(token, infoResponse.getId(), infoResponse.getLifetime());

        cacheManager.getUserTokenCache().put(token, userToken);

      } catch (WebServiceException | MailboxServerException e) {
        logger.error("ValidateUserToken with token {} server failed.", token, e);
        return Optional.empty();
      } catch (MailboxClientException e) {
        logger.error("ValidateUserToken with token {} client failed.", token, e);
        return Optional.empty();
      }
    }

    UserId userId = new UserId();
    userId.setUserId(userToken.userId());
    logger.debug(userId.getUserId());
    return Optional.of(userId);
  }

  private UserInfo createUserInfoFromAccountInfoResponse(GetAccountInfoResponse accountInfo) {
    UserInfo userInfo = new UserInfo();
    userInfo.setEmail(accountInfo.getName());
    userInfo.setDomain(accountInfo.getPublicURL());
    extractAttributesIntoUserInfo(userInfo, accountInfo.getAttr());
    return userInfo;
  }

  private UserMyself createUserMyselfFromInfoResponse(GetInfoResponse infoResponse) {
    UserMyself userMyself = new UserMyself();
    userMyself.setEmail(infoResponse.getName());
    userMyself.setDomain(infoResponse.getPublicURL());
    extractAttributesIntoUserMyself(userMyself, infoResponse.getAttrs().getAttr());
    extractPreferencesIntoUserMyself(userMyself, infoResponse.getPrefs().getPref());
    return userMyself;
  }

  private void extractAttributesIntoUserInfo(UserInfo user, List<NamedValue> attrs) {
    // The attribute zimbraIsExternalVirtualAccount is not returned if it doesn't have a value;
    // in that case the user is internal, so we default to internal and eventually it will get overwritten.
    user.setType(UserType.INTERNAL);
    // Not every user has a name, set to empty as default for compatibility
    user.setFullName("");

    for (NamedValue attribute : attrs) {
      String name = attribute.getName();
      String value = attribute.getValue();

      switch (name) {
        case "displayName":
          user.setFullName(value);
          break;
        case "zimbraId":
          UserId userId = new UserId();
          userId.setUserId(value);
          user.setId(userId);
          break;
        case "zimbraAccountStatus":
          user.setStatus(UserStatus.valueOf(value.toUpperCase()));
          break;
        case "zimbraIsExternalVirtualAccount":
          user.setType(Boolean.parseBoolean(value.toLowerCase())
            ? UserType.GUEST
            : UserType.INTERNAL);
          break;
      }
    }
  }

  private void extractAttributesIntoUserMyself(UserMyself user, List<Attr> attrs) {
    // The attribute zimbraIsExternalVirtualAccount is not returned if it doesn't have a value;
    // in that case the user is internal, so we default to internal and eventually it will get overwritten.
    user.setType(UserType.INTERNAL);
    // Not every user has a name, set to empty as default for compatibility
    user.setFullName("");

    for (Attr attribute : attrs) {
      String name = attribute.getName();
      String value = attribute.getValue();

      switch (name) {
        case "displayName":
          user.setFullName(value);
          break;
        case "zimbraId":
          UserId userId = new UserId();
          userId.setUserId(value);
          user.setId(userId);
          break;
        case "zimbraAccountStatus":
          user.setStatus(UserStatus.valueOf(value.toUpperCase()));
          break;
        case "zimbraIsExternalVirtualAccount":
          user.setType(Boolean.parseBoolean(value.toLowerCase())
            ? UserType.GUEST
            : UserType.INTERNAL);
          break;
        default:
          if (name.startsWith("carbonio")) {
            user.getCarbonioAttributes().put(name, value);
          }
          break;
      }
    }
  }

  private void extractPreferencesIntoUserMyself(UserMyself user, List<Pref> prefs) {
    // Default value for user's locale is english
    user.setLocale(Locale.ENGLISH.toString());

    for (Pref preference : prefs) {
      if (preference.getName().equals("zimbraPrefLocale")) {
        // This old style try/catch is necessary because:
        //  - the system cannot trust the user locale since it can be set manually by the sysadmin
        //    and there is no check if the value is a valid one. So the LocaleUtils#toLocale method
        //    can raise an exception if the Locale is malformed.
        //  - the project doesn't have the Vavr dependency containing the Try construct to handle
        //    the exception in a cleaner way and I don't want to add it now only for this.
        try {
          logger.debug("User myself {} requested, has locale {}", user.getId().getUserId(), preference.getValue());
          user.setLocale(LocaleUtils.toLocale(preference.getValue()).toString());
        } catch (IllegalArgumentException exception) {
          logger.error(
            "The user id {} has a locale with an invalid format. The system falls back in '{}'",
            user.getId().getUserId(),
            Locale.ENGLISH);
          user.setLocale(Locale.ENGLISH.toString());
        }
        break;
      }
    }
  }
}