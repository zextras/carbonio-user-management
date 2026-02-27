// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.grpc;

import com.zextras.carbonio.user_management.cache.record.UserDetails;
import com.zextras.carbonio.user_management.cache.record.UserInfo;
import com.zextras.carbonio.user_management.sdk.grpc.GetUserByEmailRequest;
import com.zextras.carbonio.user_management.sdk.grpc.GetUserByIdRequest;
import com.zextras.carbonio.user_management.sdk.grpc.GetUserMyselfRequest;
import com.zextras.carbonio.user_management.sdk.grpc.GetUsersRequest;
import com.zextras.carbonio.user_management.sdk.grpc.GetUsersResponse;
import com.zextras.carbonio.user_management.sdk.grpc.UserInfoProto;
import com.zextras.carbonio.user_management.sdk.grpc.UserInfoResponse;
import com.zextras.carbonio.user_management.sdk.grpc.UserManagementServiceGrpc;
import com.zextras.carbonio.user_management.sdk.grpc.UserMyselfProto;
import com.zextras.carbonio.user_management.sdk.grpc.UserMyselfResponse;
import com.zextras.carbonio.user_management.sdk.grpc.UserStatusProto;
import com.zextras.carbonio.user_management.sdk.grpc.UserTypeProto;
import com.zextras.carbonio.user_management.service.UserService;
import com.zextras.carbonio.user_management.service.UserService.MyselfResult;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.quarkus.grpc.GrpcService;
import jakarta.inject.Inject;
import java.util.List;

@GrpcService
public class GrpcHandler extends UserManagementServiceGrpc.UserManagementServiceImplBase {

  private final UserService userService;

  @Inject
  public GrpcHandler(UserService userService) {
    this.userService = userService;
  }

  @Override
  public void getUserMyself(
      GetUserMyselfRequest request,
      StreamObserver<UserMyselfResponse> responseObserver
  ) {
    String token = request.getToken();
    if (token.isBlank()) {
      responseObserver.onError(
          Status.INVALID_ARGUMENT.withDescription("Token is required").asRuntimeException());
      return;
    }

    userService.getUserMyself(token)
        .ifPresentOrElse(
            result -> {
              responseObserver.onNext(toMyselfResponse(result));
              responseObserver.onCompleted();
            },
            () -> responseObserver.onError(
                Status.UNAUTHENTICATED
                    .withDescription("Invalid or expired token")
                    .asRuntimeException()));
  }

  @Override
  public void getUserById(
      GetUserByIdRequest request,
      StreamObserver<UserInfoResponse> responseObserver
  ) {
    userService.getUserById(request.getUserId(), request.getToken())
        .ifPresentOrElse(
            userInfo -> {
              responseObserver.onNext(toUserInfoResponse(userInfo));
              responseObserver.onCompleted();
            },
            () -> responseObserver.onError(
                Status.NOT_FOUND
                    .withDescription("User not found: " + request.getUserId())
                    .asRuntimeException()));
  }

  @Override
  public void getUserByEmail(
      GetUserByEmailRequest request,
      StreamObserver<UserInfoResponse> responseObserver
  ) {
    userService.getUserByEmail(request.getUserEmail(), request.getToken())
        .ifPresentOrElse(
            userInfo -> {
              responseObserver.onNext(toUserInfoResponse(userInfo));
              responseObserver.onCompleted();
            },
            () -> responseObserver.onError(
                Status.NOT_FOUND
                    .withDescription("User not found: " + request.getUserEmail())
                    .asRuntimeException()));
  }

  @Override
  public void getUsers(
      GetUsersRequest request,
      StreamObserver<GetUsersResponse> responseObserver
  ) {
    List<UserInfo> users = userService.getUsers(
        request.getUserIdsList(), request.getToken());

    GetUsersResponse response = GetUsersResponse.newBuilder()
        .addAllUsers(users.stream().map(this::toUserInfoProto).toList())
        .build();

    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  // -- Proto conversion --

  private UserMyselfResponse toMyselfResponse(MyselfResult result) {
    UserMyselfProto.Builder myselfBuilder = UserMyselfProto.newBuilder()
        .setInfo(toUserInfoProto(result.info()))
        .setLocale(result.details().locale());

    if (result.details().carbonioAttributes() != null) {
      myselfBuilder.putAllCarbonioAttributes(result.details().carbonioAttributes());
    }

    return UserMyselfResponse.newBuilder()
        .setUser(myselfBuilder.build())
        .build();
  }

  private UserInfoResponse toUserInfoResponse(UserInfo userInfo) {
    return UserInfoResponse.newBuilder()
        .setUser(toUserInfoProto(userInfo))
        .build();
  }

  private UserInfoProto toUserInfoProto(UserInfo userInfo) {
    UserInfoProto.Builder builder = UserInfoProto.newBuilder();

    if (userInfo.userId() != null) {
      builder.setUserId(userInfo.userId());
    }
    if (userInfo.email() != null) {
      builder.setEmail(userInfo.email());
    }
    if (userInfo.fullName() != null) {
      builder.setFullName(userInfo.fullName());
    }
    if (userInfo.domain() != null) {
      builder.setDomain(userInfo.domain());
    }

    builder.setStatus(toStatusProto(userInfo.status()));
    builder.setType(toTypeProto(userInfo.type()));

    return builder.build();
  }

  private UserStatusProto toStatusProto(String status) {
    if (status == null) {
      return UserStatusProto.ACTIVE;
    }
    return switch (status.toUpperCase()) {
      case "CLOSED" -> UserStatusProto.CLOSED;
      case "LOCKED" -> UserStatusProto.LOCKED;
      case "LOCKOUT" -> UserStatusProto.LOCKOUT;
      case "MAINTENANCE" -> UserStatusProto.MAINTENANCE;
      case "PENDING" -> UserStatusProto.PENDING;
      default -> UserStatusProto.ACTIVE;
    };
  }

  private UserTypeProto toTypeProto(String type) {
    if (type == null) {
      return UserTypeProto.INTERNAL;
    }
    return switch (type.toUpperCase()) {
      case "GUEST" -> UserTypeProto.GUEST;
      default -> UserTypeProto.INTERNAL;
    };
  }
}
