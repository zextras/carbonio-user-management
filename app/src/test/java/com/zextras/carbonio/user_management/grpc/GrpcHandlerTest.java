// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.grpc;

import static com.zextras.carbonio.user_management.UserManagementServiceConfig.MAX_BATCH_USER_IDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zextras.carbonio.user_management.record.UserInfo;
import com.zextras.carbonio.user_management.record.UserMyself;
import com.zextras.carbonio.user_management.sdk.grpc.GetUserByEmailRequest;
import com.zextras.carbonio.user_management.sdk.grpc.GetUserByIdRequest;
import com.zextras.carbonio.user_management.sdk.grpc.GetUserMyselfRequest;
import com.zextras.carbonio.user_management.sdk.grpc.GetUsersRequest;
import com.zextras.carbonio.user_management.sdk.grpc.GetUsersResponse;
import com.zextras.carbonio.user_management.sdk.grpc.UserInfoResponse;
import com.zextras.carbonio.user_management.sdk.grpc.UserMyselfResponse;
import com.zextras.carbonio.user_management.sdk.grpc.UserTypeProto;
import com.zextras.carbonio.user_management.service.UserService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GrpcHandlerTest {

  private UserService userService;
  private GrpcHandler handler;

  @BeforeEach
  void setUp() {
    userService = mock(UserService.class);
    handler = new GrpcHandler(userService);
  }

  private UserInfo sampleUserInfo() {
    return new UserInfo("user-1", "user@example.com", "John Doe", "example.com", "ACTIVE", "INTERNAL");
  }

  static class TestStreamObserver<T> implements StreamObserver<T> {
    final AtomicReference<T> response = new AtomicReference<>();
    final AtomicReference<Throwable> error = new AtomicReference<>();
    boolean completed = false;

    @Override
    public void onNext(T value) { response.set(value); }
    @Override
    public void onError(Throwable t) { error.set(t); }
    @Override
    public void onCompleted() { completed = true; }
  }

  @Nested
  class GetUserMyselfTests {

    @Test
    void happyPath() {
      UserMyself myself = new UserMyself(
          "user-1", "user@example.com", "John Doe", "example.com",
          "ACTIVE", "INTERNAL", "it",
          List.of("carbonioFeatureFilesEnabled"),
          Map.of("carbonioWscMaxGroupMembers", "50"));
      when(userService.getUserMyself("token-1")).thenReturn(Optional.of(myself));

      TestStreamObserver<UserMyselfResponse> observer = new TestStreamObserver<>();
      handler.getUserMyself(
          GetUserMyselfRequest.newBuilder().setToken("token-1").build(), observer);

      assertThat(observer.completed).isTrue();
      assertThat(observer.response.get().getUser().getInfo().getUserId()).isEqualTo("user-1");
      assertThat(observer.response.get().getUser().getLocale()).isEqualTo("it");
      assertThat(observer.response.get().getUser().getFeaturesList())
          .containsExactly("carbonioFeatureFilesEnabled");
      assertThat(observer.response.get().getUser().getCapabilitiesMap())
          .containsEntry("carbonioWscMaxGroupMembers", "50");
    }

    @Test
    void returnsUnauthenticatedOnEmpty() {
      when(userService.getUserMyself("bad-token")).thenReturn(Optional.empty());

      TestStreamObserver<UserMyselfResponse> observer = new TestStreamObserver<>();
      handler.getUserMyself(
          GetUserMyselfRequest.newBuilder().setToken("bad-token").build(), observer);

      assertThat(observer.error.get()).isInstanceOf(StatusRuntimeException.class);
      assertThat(((StatusRuntimeException) observer.error.get()).getStatus().getCode())
          .isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    @Test
    void returnsInvalidArgumentOnBlankToken() {
      TestStreamObserver<UserMyselfResponse> observer = new TestStreamObserver<>();
      handler.getUserMyself(
          GetUserMyselfRequest.newBuilder().setToken("").build(), observer);

      assertThat(observer.error.get()).isInstanceOf(StatusRuntimeException.class);
      assertThat(((StatusRuntimeException) observer.error.get()).getStatus().getCode())
          .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }
  }

  @Nested
  class GetUserByIdTests {

    @Test
    void happyPath() {
      when(userService.getUserById("user-1"))
          .thenReturn(Optional.of(sampleUserInfo()));

      TestStreamObserver<UserInfoResponse> observer = new TestStreamObserver<>();
      handler.getUserById(
          GetUserByIdRequest.newBuilder().setToken("token-1").setUserId("user-1").build(), observer);

      assertThat(observer.completed).isTrue();
      assertThat(observer.response.get().getUser().getUserId()).isEqualTo("user-1");
      assertThat(observer.response.get().getUser().getEmail()).isEqualTo("user@example.com");
      assertThat(observer.response.get().getUser().getStatus()).isEqualTo("ACTIVE");
      assertThat(observer.response.get().getUser().getType()).isEqualTo(UserTypeProto.INTERNAL);
    }

    @Test
    void returnsNotFoundOnEmpty() {
      when(userService.getUserById("missing")).thenReturn(Optional.empty());

      TestStreamObserver<UserInfoResponse> observer = new TestStreamObserver<>();
      handler.getUserById(
          GetUserByIdRequest.newBuilder().setToken("token-1").setUserId("missing").build(), observer);

      assertThat(observer.error.get()).isInstanceOf(StatusRuntimeException.class);
      assertThat(((StatusRuntimeException) observer.error.get()).getStatus().getCode())
          .isEqualTo(Status.Code.NOT_FOUND);
    }
  }

  @Nested
  class GetUserByEmailTests {

    @Test
    void happyPath() {
      when(userService.getUserByEmail("user@example.com"))
          .thenReturn(Optional.of(sampleUserInfo()));

      TestStreamObserver<UserInfoResponse> observer = new TestStreamObserver<>();
      handler.getUserByEmail(
          GetUserByEmailRequest.newBuilder().setToken("token-1").setUserEmail("user@example.com").build(), observer);

      assertThat(observer.completed).isTrue();
      assertThat(observer.response.get().getUser().getFullName()).isEqualTo("John Doe");
    }

    @Test
    void returnsNotFoundOnEmpty() {
      when(userService.getUserByEmail("nope@x.com")).thenReturn(Optional.empty());

      TestStreamObserver<UserInfoResponse> observer = new TestStreamObserver<>();
      handler.getUserByEmail(
          GetUserByEmailRequest.newBuilder().setToken("token-1").setUserEmail("nope@x.com").build(), observer);

      assertThat(observer.error.get()).isInstanceOf(StatusRuntimeException.class);
      assertThat(((StatusRuntimeException) observer.error.get()).getStatus().getCode())
          .isEqualTo(Status.Code.NOT_FOUND);
    }
  }

  @Nested
  class GetUsersTests {

    @Test
    void happyPath() {
      UserInfo u1 = new UserInfo("id-1", "a@x.com", "A", "x.com", "ACTIVE", "INTERNAL");
      UserInfo u2 = new UserInfo("id-2", "b@x.com", "B", "x.com", "CLOSED", "GUEST");

      when(userService.getUsers(anyList())).thenReturn(List.of(u1, u2));

      TestStreamObserver<GetUsersResponse> observer = new TestStreamObserver<>();
      handler.getUsers(
          GetUsersRequest.newBuilder().setToken("token-1").addAllUserIds(List.of("id-1", "id-2")).build(), observer);

      assertThat(observer.completed).isTrue();
      assertThat(observer.response.get().getUsersList()).hasSize(2);
      assertThat(observer.response.get().getUsers(0).getUserId()).isEqualTo("id-1");
      assertThat(observer.response.get().getUsers(1).getStatus()).isEqualTo("CLOSED");
      assertThat(observer.response.get().getUsers(1).getType()).isEqualTo(UserTypeProto.GUEST);
    }

    @Test
    void returnsEmptyListWhenNoUsersFound() {
      when(userService.getUsers(anyList())).thenReturn(List.of());

      TestStreamObserver<GetUsersResponse> observer = new TestStreamObserver<>();
      handler.getUsers(
          GetUsersRequest.newBuilder().setToken("token-1").addAllUserIds(List.of("bad-id")).build(), observer);

      assertThat(observer.completed).isTrue();
      assertThat(observer.response.get().getUsersList()).isEmpty();
    }

    @Test
    void returnsInvalidArgumentWhenExceedingMaxBatch() {
      var builder = GetUsersRequest.newBuilder().setToken("token-1");
      for (int i = 0; i < MAX_BATCH_USER_IDS + 1; i++) {
        builder.addUserIds("id-" + i);
      }

      TestStreamObserver<GetUsersResponse> observer = new TestStreamObserver<>();
      handler.getUsers(builder.build(), observer);

      assertThat(observer.error.get()).isInstanceOf(StatusRuntimeException.class);
      assertThat(((StatusRuntimeException) observer.error.get()).getStatus().getCode())
          .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void returnsInvalidArgumentWhenExactlyAtMaxPlusOne() {
      var builder = GetUsersRequest.newBuilder().setToken("token-1");
      for (int i = 0; i < MAX_BATCH_USER_IDS + 1; i++) {
        builder.addUserIds("id-" + i);
      }

      TestStreamObserver<GetUsersResponse> observer = new TestStreamObserver<>();
      handler.getUsers(builder.build(), observer);

      assertThat(observer.error.get()).isNotNull();
    }

    @Test
    void acceptsExactlyMaxBatchIds() {
      var builder = GetUsersRequest.newBuilder().setToken("token-1");
      for (int i = 0; i < MAX_BATCH_USER_IDS; i++) {
        builder.addUserIds("id-" + i);
      }
      when(userService.getUsers(anyList())).thenReturn(List.of());

      TestStreamObserver<GetUsersResponse> observer = new TestStreamObserver<>();
      handler.getUsers(builder.build(), observer);

      assertThat(observer.completed).isTrue();
      assertThat(observer.error.get()).isNull();
    }
  }
}
