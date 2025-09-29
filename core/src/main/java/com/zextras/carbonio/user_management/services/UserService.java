// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.services;

import static com.zextras.mailbox.client.service.ServiceRequests.AccountInfo;
import static com.zextras.mailbox.client.service.ServiceRequests.Info;

import com.google.inject.Inject;
import com.sun.xml.ws.protocol.soap.MessageCreationException;
import com.zextras.carbonio.user_management.cache.CacheManager;
import com.zextras.carbonio.user_management.entities.UserToken;
import com.zextras.carbonio.user_management.generated.model.UserId;
import com.zextras.carbonio.user_management.generated.model.UserInfo;
import com.zextras.carbonio.user_management.generated.model.UserMyself;
import com.zextras.carbonio.user_management.generated.model.UserStatus;
import com.zextras.carbonio.user_management.generated.model.UserType;
import com.zextras.mailbox.client.MailboxClientException;
import com.zextras.mailbox.client.MailboxServerException;
import com.zextras.mailbox.client.requests.Request;
import com.zextras.mailbox.client.service.InfoRequests.Sections;
import com.zextras.mailbox.client.service.ServiceClient;
import com.zextras.wsdl.zimbraservice.ZcsPortType;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import jakarta.xml.ws.WebServiceException;
import org.apache.commons.lang3.LocaleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zimbraaccount.Attr;
import zimbraaccount.GetAccountInfoResponse;
import zimbraaccount.GetInfoResponse;

public class UserService {

  private static final Logger logger = LoggerFactory.getLogger(UserService.class);

  private final CacheManager cacheManager;
  private final ServiceClient mailboxClient;

  @Inject
  public UserService(CacheManager cacheManager, ServiceClient mailboxClient) {
    this.cacheManager = cacheManager;
    this.mailboxClient = mailboxClient;
  }

  private UserInfo createUserInfo(GetAccountInfoResponse accountInfo) {
    UserInfo userInfo = new UserInfo();

    // default value in case zimbraIsExternalVirtualAccount is not returned
    userInfo.setType(UserType.INTERNAL);
    // default value in case status is not returned
    userInfo.setStatus(UserStatus.CLOSED);

    accountInfo
        .getAttr()
        .forEach(
            attribute -> {
              if (attribute.getName().equals("displayName")) {
                userInfo.setFullName(attribute.getValue());
              }

              if (attribute.getName().equals("zimbraId")) {
                UserId userId = new UserId();
                userId.setUserId(attribute.getValue());
                userInfo.setId(userId);
              }

              if (attribute.getName().equals("zimbraAccountStatus")) {
                userInfo.setStatus(UserStatus.valueOf(attribute.getValue().toUpperCase()));
              }

              if (attribute.getName().equals("zimbraIsExternalVirtualAccount")) {
                userInfo.setType(
                    Boolean.parseBoolean(attribute.getValue().toLowerCase())
                        ? UserType.GUEST
                        : UserType.INTERNAL);
              }
            });

    userInfo.setEmail(accountInfo.getName());
    userInfo.setDomain(accountInfo.getPublicURL());

    return userInfo;
  }

  public Response getUsers(List<String> userIds, String token, Boolean ignoreCache) {
    return Response.ok()
        .entity(
            userIds.stream()
                .distinct()
                .map(
                    userId -> {
                      logger.info("Requested: {}", userId);

                      UserInfo userInfo = null;
                      if (ignoreCache == null || !ignoreCache) {
                        userInfo = cacheManager.getUserByIdCache().getIfPresent(userId);
                      }

                      if (userInfo == null) {
                        try {
                          final Request<ZcsPortType, GetAccountInfoResponse> request =
                              AccountInfo.byId(userId).withAuthToken(token);
                          final GetAccountInfoResponse accountInfo = mailboxClient.send(request);

                          userInfo = createUserInfo(accountInfo);
                          cacheManager.getUserByIdCache().put(userId, userInfo);
                          cacheManager.getUserByEmailCache().put(userInfo.getEmail(), userInfo);
                          logger.info("Found: {}", userId);
                        } catch (MailboxServerException e) {
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
    logger.info("Requested: {}", userId);

    UserInfo userInfo = null;
    if (ignoreCache == null || !ignoreCache) {
      userInfo = cacheManager.getUserByIdCache().getIfPresent(userId);
    }

    if (userInfo == null) {
      try {
        final Request<ZcsPortType, GetAccountInfoResponse> request =
            AccountInfo.byId(userId).withAuthToken(token);
        final GetAccountInfoResponse accountInfo = mailboxClient.send(request);

        userInfo = createUserInfo(accountInfo);
        cacheManager.getUserByIdCache().put(userId, userInfo);
        cacheManager.getUserByEmailCache().put(userInfo.getEmail(), userInfo);

      } catch (MailboxServerException e) {
        logger.error("GetInfoById with userId {} and token {} server failed.", userId, token, e);
        return Optional.empty();
      } catch (MailboxClientException e) {
        logger.error("GetInfoById with userId {} and token {} client failed.", userId, token, e);
        return Optional.empty();
      }
    }
    logger.info(userInfo.getId().getUserId());
    return Optional.of(userInfo);
  }

  public Optional<UserInfo> getInfoByEmail(String userEmail, String token, Boolean ignoreCache) {
    logger.info("Requested: {}", userEmail);

    UserInfo userInfo = null;
    if (ignoreCache == null || !ignoreCache) {
      userInfo = cacheManager.getUserByEmailCache().getIfPresent(userEmail);
    }

    if (userInfo == null) {
      try {
        final Request<ZcsPortType, GetAccountInfoResponse> request =
            AccountInfo.byEmail(userEmail).withAuthToken(token);
        final GetAccountInfoResponse accountInfo = mailboxClient.send(request);

        userInfo = createUserInfo(accountInfo);
        cacheManager.getUserByEmailCache().put(userEmail, userInfo);
        cacheManager.getUserByIdCache().put(userInfo.getId().getUserId(), userInfo);

      } catch (MailboxServerException e) {
        logger.error("GetInfoByEmail with user email {} and token {} server failed.", userEmail, token, e);
        return Optional.empty();
      } catch (MailboxClientException e) {
        logger.error("GetInfoByEmail with user email {} and token {} client failed.", userEmail, token, e);
        return Optional.empty();
      }
    }
    logger.info(userInfo.getId().getUserId());
    return Optional.of(userInfo);
  }

  public Optional<UserMyself> getMyselfByToken(String token, Boolean ignoreCache) {
    logger.info("Requested: {}", token);

    UserMyself userMyself = null;
    if (ignoreCache == null || !ignoreCache) {
      userMyself = cacheManager.getUserMyselfCache().getIfPresent(token);
    }

    if (userMyself == null) {
      try {
        final Request<ZcsPortType, GetInfoResponse> request =
            Info.sections(Sections.children, Sections.attrs, Sections.prefs).withAuthToken(token);
        final GetInfoResponse infoResponse = mailboxClient.send(request);

        UserId userId = new UserId();
        userId.setUserId(infoResponse.getId());

        userMyself = new UserMyself();
        userMyself.setId(userId);
        userMyself.setEmail(infoResponse.getName());
        userMyself.setDomain(infoResponse.getPublicURL());
        userMyself.setFullName(readFullName(infoResponse));
        userMyself.setLocale(readLocal(infoResponse, userId).toString());
        userMyself.setType(readUserType(infoResponse));

        cacheManager.getUserMyselfCache().put(token, userMyself);

      } catch (WebServiceException | MailboxServerException exception) {
        logger.error("GetMyselfByToken with token {} server failed.", token, exception);
        return Optional.empty();
      } catch (MailboxClientException exception) {
        logger.error("GetMyselfByToken with token {} client failed.", token, exception);
        return Optional.empty();
      }
    }

    return Optional.of(userMyself);
  }

  public Optional<UserId> validateUserToken(String token) {
    logger.info("Validate: {}", token);
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

      } catch (MailboxServerException e) {
        logger.error("ValidateUserToken with token {} server failed.", token, e);
        return Optional.empty();
      } catch (MailboxClientException e) {
        logger.error("ValidateUserToken with token {} client failed.", token, e);
        return Optional.empty();
      }
    }

    UserId userId = new UserId();
    userId.setUserId(userToken.getUserId());
    logger.info(userId.getUserId());
    return Optional.of(userId);
  }

  private static Locale readLocal(GetInfoResponse infoResponse, UserId userId) {
    // This old style try/catch is necessary because:
    //  - the system cannot trust the user locale since it can be set manually by the sysadmin
    //    and there is no check if the value is a valid one. So the LocaleUtils#toLocale method
    //    can raise an exception if the Locale is malformed.
    //  - the project doesn't have the Vavr dependency containing the Try construct to handle
    //    the exception in a cleaner way and I don't want to add it now only for this.
    try {
      return infoResponse.getPrefs().getPref().stream()
          .filter(perf -> perf.getName().equals("zimbraPrefLocale"))
          .findFirst()
          .map(pref -> {
            logger.info("User myself {} requested, has locale {}", userId.getUserId(), pref.getValue());
            return LocaleUtils.toLocale(pref.getValue());
          })
          .orElse(Locale.ENGLISH);
    } catch (IllegalArgumentException exception) {
      logger.error(
        "The user id {} has a locale with an invalid format. The system falls back in '{}'",
        userId.getUserId(),
        Locale.ENGLISH);

      return Locale.ENGLISH;
    }
  }

  private static String readFullName(GetInfoResponse infoResponse) {
    return infoResponse.getAttrs().getAttr().stream()
      .filter(attribute -> attribute.getName().equals("displayName"))
      .findFirst()
      .map(Attr::getValue)
      .orElse("");
  }

  private static UserType readUserType(GetInfoResponse infoResponse) {
    return Boolean.parseBoolean(
      infoResponse.getAttrs().getAttr().stream()
        .filter(
          attribute ->
            attribute.getName().equals("zimbraIsExternalVirtualAccount"))
        .findFirst()
        .map(Attr::getValue)
        .orElse("FALSE") // default value, will be translated to type internal
        .toLowerCase())
      ? UserType.GUEST
      : UserType.INTERNAL;
  }
}
