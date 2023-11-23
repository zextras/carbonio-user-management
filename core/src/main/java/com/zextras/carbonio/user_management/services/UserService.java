// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.services;

import static com.zextras.mailbox.client.service.ServiceRequests.AccountInfo;
import static com.zextras.mailbox.client.service.ServiceRequests.Info;

import com.google.inject.Inject;
import com.sun.xml.ws.fault.ServerSOAPFaultException;
import com.zextras.carbonio.user_management.cache.CacheManager;
import com.zextras.carbonio.user_management.entities.UserToken;
import com.zextras.carbonio.user_management.exceptions.ServiceException;
import com.zextras.carbonio.user_management.generated.model.UserId;
import com.zextras.carbonio.user_management.generated.model.UserInfo;
import com.zextras.carbonio.user_management.generated.model.UserMyself;
import com.zextras.mailbox.client.requests.Request;
import com.zextras.mailbox.client.service.InfoRequests.Sections;
import com.zextras.mailbox.client.service.ServiceClient;
import https.www_zextras_com.wsdl.zimbraservice.ZcsPortType;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
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

    accountInfo.getAttr().forEach(attribute -> {
      if (attribute.getName().equals("displayName")) {
        userInfo.setFullName(attribute.getValue());
      }

      if (attribute.getName().equals("zimbraId")) {
        UserId userId = new UserId();
        userId.setUserId(attribute.getValue());
        userInfo.setId(userId);
      }
    });

    userInfo.setEmail(accountInfo.getName());
    userInfo.setDomain(accountInfo.getPublicURL());

    return userInfo;
  }

  public Response getUsers(List<String> userIds, String token) {
    return Response.ok().entity(
      userIds.stream().distinct().map(userId -> {
        System.out.println("Requested: " + userId);
        UserInfo userInfo = cacheManager.getUserByIdCache().getIfPresent(userId);

        if (userInfo == null) {
          try {
            final Request<ZcsPortType, GetAccountInfoResponse> request =
              AccountInfo.byId(userId).withAuthToken(token);
            final GetAccountInfoResponse accountInfo = mailboxClient.send(request);

            userInfo = createUserInfo(accountInfo);
            cacheManager.getUserByIdCache().put(userId, userInfo);
            cacheManager.getUserByEmailCache().put(userInfo.getEmail(), userInfo);
            System.out.println("Found: " + userId);
          } catch (Exception e) {
            logger.error("GetUsers with userId {} and token {} falied: {}", userId, token, e);
          }
        }
        return userInfo;
      }).filter(Objects::nonNull).toList()
    ).build();
  }

  public Response getInfoById(
    String userId,
    String token
  ) {
    System.out.println("Requested: " + userId);
    UserInfo userInfo = cacheManager.getUserByIdCache().getIfPresent(userId);

    if (userInfo == null) {
      try {
        final Request<ZcsPortType, GetAccountInfoResponse> request =
          AccountInfo.byId(userId).withAuthToken(token);
        final GetAccountInfoResponse accountInfo = mailboxClient.send(request);

        userInfo = createUserInfo(accountInfo);
        cacheManager.getUserByIdCache().put(userId, userInfo);
        cacheManager.getUserByEmailCache().put(userInfo.getEmail(), userInfo);

      } catch (ServerSOAPFaultException e) {
        logger.error("GetInfoById with userId {} and token {} falied: {}", userId, token, e);
        return Response.status(Status.NOT_FOUND).build();
      } catch (Exception e) {
        logger.error("GetInfoById with userId {} and token {} falied: {}", userId, token, e);
        return Response.status(Status.INTERNAL_SERVER_ERROR).build();
      }
    }

    System.out.println(userInfo.getId().getUserId());
    return Response.ok().entity(userInfo).build();
  }

  public Response getInfoByEmail(
    String userEmail,
    String token
  ) {
    System.out.println("Requested: " + userEmail);
    UserInfo userInfo = cacheManager.getUserByEmailCache().getIfPresent(userEmail);

    if (userInfo == null) {
      try {
        final Request<ZcsPortType, GetAccountInfoResponse> request =
          AccountInfo.byEmail(userEmail).withAuthToken(token);
        final GetAccountInfoResponse accountInfo = mailboxClient.send(request);

        userInfo = createUserInfo(accountInfo);
        cacheManager.getUserByEmailCache().put(userEmail, userInfo);
        cacheManager.getUserByIdCache().put(userInfo.getId().getUserId(), userInfo);

      } catch (ServerSOAPFaultException e) {
        logger.error("GetInfoByEmail with user email {} and token {} failed: {}", userEmail, token,
          e);
        return Response.status(Status.NOT_FOUND).build();
      } catch (Exception e) {
        logger.error("GetInfoByEmail with user email {} and token {} failed: {}", userEmail, token,
          e);
        return Response.status(Status.INTERNAL_SERVER_ERROR).build();
      }
    }
    System.out.println(userInfo.getId().getUserId());

    return Response.ok().entity(userInfo).build();
  }

  public Optional<UserMyself> getMyselfByToken(String token) {
    try {
      final Request<ZcsPortType, GetInfoResponse> request =
        Info.sections(Sections.children, Sections.attrs, Sections.prefs).withAuthToken(token);
      final GetInfoResponse infoResponse = mailboxClient.send(request);

      UserId userId = new UserId();
      userId.setUserId(infoResponse.getId());

      // This old style try/catch is necessary because:
      //  - the system cannot trust the user locale since it can be set manually by the sysadmin
      //    and there is no check if the value is a valid one. So the LocaleUtils#toLocale method
      //    can raise an exception if the Locale is malformed.
      //  - the project doesn't have the Vavr dependency containing the Try construct to handle the
      //    exception in a cleaner way and I don't want to add it now only for this.
      Locale locale;
      try {
        locale = infoResponse
          .getPrefs()
          .getPref()
          .stream()
          .filter(perf -> perf.getName().equals("zimbraPrefLocale"))
          .findFirst()
          .map(pref -> LocaleUtils.toLocale(pref.getValue()))
          .orElse(Locale.ENGLISH);
      } catch (IllegalArgumentException exception) {
        logger.error(
          "The user id {} has a locale with an invalid format. The system falls back in '{}'",
          userId.getUserId(),
          Locale.ENGLISH
        );

        locale = Locale.ENGLISH;
      }

      String fullName = infoResponse
        .getAttrs()
        .getAttr()
        .stream()
        .filter(attribute -> attribute.getName().equals("displayName"))
        .findFirst()
        .map(Attr::getValue)
        .orElse("");

      UserMyself userMyself = new UserMyself();
      userMyself.setId(userId);
      userMyself.setEmail(infoResponse.getName());
      userMyself.setDomain(infoResponse.getPublicURL());
      userMyself.setFullName(fullName);
      userMyself.setLocale(locale.toString());

      return Optional.of(userMyself);

    } catch (ServerSOAPFaultException exception) {
      logger.error("GetMyselfByToken with token {} failed: {}", token, exception);
      return Optional.empty();
    } catch (Exception exception) {
      logger.error("GetMyselfByToken with token {} failed: {}", token, exception);
      throw new ServiceException(
        "Unable to get account user info due to an internal service error");
    }
  }

  public Response validateUserToken(String token) {
    System.out.println("Validate: " + token);
    // We can't use Optional.ofNullable because validateAuthToken throws exceptions and
    // we need to return different status codes based on different exceptions
    UserToken userToken = cacheManager.getUserTokenCache().getIfPresent(token);

    if (userToken == null) {
      try {
        final Request<ZcsPortType, GetInfoResponse> request =
          Info.sections(Sections.children).withAuthToken(token);
        final GetInfoResponse infoResponse = mailboxClient.send(request);

        userToken = new UserToken(
          token,
          infoResponse.getId(),
          infoResponse.getLifetime()
        );

        cacheManager.getUserTokenCache().put(token, userToken);

      } catch (ServerSOAPFaultException e) {
        logger.error("ValidateUserToken with token {} failed: {}", token, e);
        return Response.status(Status.UNAUTHORIZED).build();
      } catch (Exception e) {
        logger.error("ValidateUserToken with token {} failed: {}", token, e);
        return Response.status(Status.INTERNAL_SERVER_ERROR).build();
      }
    }

    UserId userId = new UserId();
    userId.setUserId(userToken.getUserId());
    System.out.println(userId.getUserId());
    return Response.ok().entity(userId).build();
  }
}
