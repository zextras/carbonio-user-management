// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.services;

import client.SoapClient;
import com.google.inject.Inject;
import com.sun.xml.ws.fault.ServerSOAPFaultException;
import com.zextras.carbonio.user_management.cache.CacheManager;
import com.zextras.carbonio.user_management.entities.UserToken;
import com.zextras.carbonio.user_management.generated.model.UserDetailsDto;
import com.zextras.carbonio.user_management.generated.model.UserIdDto;
import com.zextras.carbonio.user_management.generated.model.UserInfoDto;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import zimbraaccount.GetAccountInfoResponse;
import zimbraaccount.GetInfoResponse;

public class UserService {

  private final CacheManager cacheManager;

  @Inject
  public UserService(CacheManager cacheManager) {
    this.cacheManager = cacheManager;
  }

  private UserInfoDto createUserInfo(GetAccountInfoResponse accountInfo) {
    UserInfoDto userInfo = new UserInfoDto();

    accountInfo.getAttr().forEach(attribute -> {
      if (attribute.getName().equals("displayName")) {
        userInfo.setFullName(attribute.getValue());
      }

      if (attribute.getName().equals("zimbraId")) {
        userInfo.setId(UUID.fromString(attribute.getValue()));
      }
    });

    userInfo.setEmail(accountInfo.getName());
    userInfo.setDomain(accountInfo.getPublicURL());

    return userInfo;
  }

  private UserDetailsDto createUserDetails(GetAccountInfoResponse accountInfo) {
    UserDetailsDto userDetails = new UserDetailsDto();

    accountInfo.getAttr().forEach(attribute -> {
      if (attribute.getName().equals("displayName")) {
        userDetails.getUserInfo().setFullName(attribute.getValue());
      }

      if (attribute.getName().equals("zimbraId")) {
        userDetails.getUserInfo().setId(UUID.fromString(attribute.getValue()));
      }
    });

    userDetails.getUserInfo().setEmail(accountInfo.getName());
    userDetails.getUserInfo().setDomain(accountInfo.getPublicURL());

    // TODO add pictureUpdatedAt and statusMessage

    return userDetails;
  }

  public List<UserDetailsDto> getUsers(List<String> userIds, String token) {
    List<UserDetailsDto> usersDetails = new ArrayList<>();

    userIds.forEach(userId -> {
      System.out.println("Requested: " + userId);
      try {
        GetAccountInfoResponse accountInfo = SoapClient
          .newClient()
          .setAuthToken(token)
          .getAccountInfoById(UUID.fromString(userId));
        usersDetails.add(createUserDetails(accountInfo));
      } catch (ServerSOAPFaultException e) {
        e.printStackTrace();
        //return Response.status(Status.NOT_FOUND).build();
      } catch (Exception e) {
        e.printStackTrace();
        //return Response.status(Status.INTERNAL_SERVER_ERROR).build();
      }
    });
    return usersDetails;
  }

  public Response getInfoById(
    UUID userUuid,
    String token
  ) {
    System.out.println("Requested: " + userUuid);
    UserInfoDto userInfo = cacheManager.getUserByIdCache().getIfPresent(userUuid);

    if (userInfo == null) {
      try {
        GetAccountInfoResponse accountInfo = SoapClient
          .newClient()
          .setAuthToken(token)
          .getAccountInfoById(userUuid);

        userInfo = createUserInfo(accountInfo);
        cacheManager.getUserByIdCache().put(userUuid, userInfo);
        cacheManager.getUserByEmailCache().put(userInfo.getEmail(), userInfo);

      } catch (ServerSOAPFaultException e) {
        e.printStackTrace();
        return Response.status(Status.NOT_FOUND).build();
      } catch (Exception e) {
        e.printStackTrace();
        return Response.status(Status.INTERNAL_SERVER_ERROR).build();
      }
    }

    System.out.println(userInfo.getId());
    return Response.ok().entity(userInfo).build();
  }

  public Response getInfoByEmail(
    String userEmail,
    String token
  ) {
    System.out.println("Requested: " + userEmail);
    UserInfoDto userInfo = cacheManager.getUserByEmailCache().getIfPresent(userEmail);

    if (userInfo == null) {
      try {
        GetAccountInfoResponse accountInfo = SoapClient
          .newClient()
          .setAuthToken(token)
          .getAccountInfoByEmail(userEmail);

        userInfo = createUserInfo(accountInfo);
        cacheManager.getUserByEmailCache().put(userEmail, userInfo);
        cacheManager.getUserByIdCache().put(userInfo.getId(), userInfo);

      } catch (ServerSOAPFaultException e) {
        e.printStackTrace();
        return Response.status(Status.NOT_FOUND).build();
      } catch (Exception e) {
        e.printStackTrace();
        return Response.status(Status.INTERNAL_SERVER_ERROR).build();
      }
    }
    System.out.println(userInfo.getId());

    return Response.ok().entity(userInfo).build();
  }

  public Response validateUserToken(String token) {
    System.out.println("Validate: " + token);
    // We can't use Optional.ofNullable because validateAuthToken throws exceptions and
    // we need to return different status codes based on different exceptions
    UserToken userToken = cacheManager.getUserTokenCache().getIfPresent(token);

    if (userToken == null) {
      try {
        GetInfoResponse infoResponse = SoapClient
          .newClient()
          .setAuthToken(token)
          .validateAuthToken();

        userToken = new UserToken(
          token,
          UUID.fromString(infoResponse.getId()),
          infoResponse.getLifetime()
        );

        cacheManager.getUserTokenCache().put(token, userToken);

      } catch (ServerSOAPFaultException e) {
        e.printStackTrace();
        return Response.status(Status.UNAUTHORIZED).build();
      } catch (Exception e) {
        e.printStackTrace();
        return Response.status(Status.INTERNAL_SERVER_ERROR).build();
      }
    }

    UserIdDto userId = new UserIdDto();
    userId.setUserId(userToken.getUserId());
    System.out.println(userId.getUserId());
    return Response.ok().entity(userId).build();
  }
}
