// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.services;

import client.SoapClient;
import com.google.inject.Inject;
import com.sun.xml.ws.fault.ServerSOAPFaultException;
import com.zextras.carbonio.user_management.cache.CacheManager;
import com.zextras.carbonio.user_management.dal.dao.User;
import com.zextras.carbonio.user_management.dal.repository.UserRepository;
import com.zextras.carbonio.user_management.entities.UserToken;
import com.zextras.carbonio.user_management.generated.model.UserDetails;
import com.zextras.carbonio.user_management.generated.model.UserId;
import com.zextras.carbonio.user_management.generated.model.UserInfo;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import zimbraaccount.GetAccountInfoResponse;
import zimbraaccount.GetInfoResponse;

public class UserService {

  private final CacheManager cacheManager;
  private final UserRepository userRepository;

  @Inject
  public UserService(CacheManager cacheManager, UserRepository userRepository) {
    this.cacheManager = cacheManager;
    this.userRepository = userRepository;
  }

  private UserInfo createUserInfo(GetAccountInfoResponse accountInfo) {
    UserInfo userInfo = new UserInfo();

    accountInfo.getAttr().forEach(attribute -> {
      if (attribute.getName().equals("displayName")) {
        userInfo.setFullName(attribute.getValue());
      }

      if (attribute.getName().equals("zimbraId")) {
        UserId userId = new UserId();
        userId.setUserId(UUID.fromString(attribute.getValue()));
        userInfo.setId(userId);
      }
    });

    userInfo.setEmail(accountInfo.getName());
    userInfo.setDomain(accountInfo.getPublicURL());

    return userInfo;
  }

  private UserDetails createUserDetails(GetAccountInfoResponse accountInfo, Optional<User> user) {
    UserDetails userDetails = new UserDetails();

    accountInfo.getAttr().forEach(attribute -> {
      if (attribute.getName().equals("displayName")) {
        userDetails.getUserInfo().setFullName(attribute.getValue());
      }

      if (attribute.getName().equals("zimbraId")) {
        UserId userId = new UserId();
        userId.setUserId(UUID.fromString(attribute.getValue()));
        userDetails.getUserInfo().setId(userId);
      }
    });

    userDetails.getUserInfo().setEmail(accountInfo.getName());
    userDetails.getUserInfo().setDomain(accountInfo.getPublicURL());
    user.ifPresent(u -> {
      userDetails.setPictureUpdatedAt(Date.from(u.getPictureUpdatedAt().toInstant()));
      userDetails.setStatusMessage(u.getStatusMessage());
    });
    return userDetails;
  }

  public List<UserDetails> getUsers(List<UserId> userIds, String token) {
    List<UserDetails> usersDetails = new ArrayList<>();

    userIds.forEach(userId -> {
      System.out.println("Requested: " + userId);
      try {
        GetAccountInfoResponse accountInfo = SoapClient
          .newClient()
          .setAuthToken(token)
          .getAccountInfoById(userId.getUserId());
        usersDetails.add(createUserDetails(accountInfo, userRepository.getById(userId.getUserId())));
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
    UserId userUuid,
    String token
  ) {
    System.out.println("Requested: " + userUuid);
    UserInfo userInfo = cacheManager.getUserByIdCache().getIfPresent(userUuid);

    if (userInfo == null) {
      try {
        GetAccountInfoResponse accountInfo = SoapClient
          .newClient()
          .setAuthToken(token)
          .getAccountInfoById(userUuid.getUserId());

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
    UserInfo userInfo = cacheManager.getUserByEmailCache().getIfPresent(userEmail);

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

    UserId userId = new UserId();
    userId.setUserId(userToken.getUserId());
    System.out.println(userId.getUserId());
    return Response.ok().entity(userId).build();
  }
}
