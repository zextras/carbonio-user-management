// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zextras.carbonio.user_management.BaseIT;
import com.zextras.carbonio.user_management.MailboxStackTestResource;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * gRPC endpoint integration tests against a real Carbonio mailbox stack.
 *
 * <p>Run with: {@code mvn verify -Dskip.integration.tests=false}
 */
@QuarkusIntegrationTest
@WithTestResource(MailboxStackTestResource.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GrpcIT extends BaseIT {

  private io.grpc.ManagedChannel channel;
  private com.zextras.carbonio.user_management.sdk.grpc.UserManagementServiceGrpc.UserManagementServiceBlockingStub stub;

  @BeforeAll
  void setUpGrpc() {
    // gRPC runs on the same port as HTTP (use-separate-server=false).
    // RestAssured.port is reliably set by the Quarkus test framework via
    // MicroProfile Config (quarkus.http.test-port). Prefer it over restPort,
    // which may be 0 when test.url is not resolvable in the failsafe JVM.
    int grpcPort = io.restassured.RestAssured.port > 0
        ? io.restassured.RestAssured.port
        : Integer.getInteger("quarkus.http.port", 8081);
    channel = io.grpc.Grpc.newChannelBuilderForAddress(
            "localhost", grpcPort, io.grpc.InsecureChannelCredentials.create())
        .build();
    stub = com.zextras.carbonio.user_management.sdk.grpc.UserManagementServiceGrpc
        .newBlockingStub(channel);
  }

  @AfterAll
  void tearDownGrpc() throws Exception {
    if (channel != null) {
      channel.shutdownNow().awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
    }
  }

  @Test
  void getUserMyselfReturnsUser() {
    var response = stub.getUserMyself(
        com.zextras.carbonio.user_management.sdk.grpc.GetUserMyselfRequest.newBuilder()
            .setToken(authToken).build());
    assertEquals(TEST_USER_EMAIL, response.getUser().getInfo().getEmail());
    assertEquals(testUserId, response.getUser().getInfo().getUserId());
    assertNotNull(response.getUser().getLocale());
  }

  @Test
  void getUserMyselfWithInvalidTokenReturnsUnauthenticated() {
    StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
        stub.getUserMyself(
            com.zextras.carbonio.user_management.sdk.grpc.GetUserMyselfRequest.newBuilder()
                .setToken("invalid-token").build()));
    assertEquals(Status.Code.UNAUTHENTICATED, e.getStatus().getCode());
  }

  @Test
  void getUserMyselfWithBlankTokenReturnsInvalidArgument() {
    StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
        stub.getUserMyself(
            com.zextras.carbonio.user_management.sdk.grpc.GetUserMyselfRequest.newBuilder()
                .setToken("").build()));
    assertEquals(Status.Code.INVALID_ARGUMENT, e.getStatus().getCode());
  }

  @Test
  void getUserByIdReturnsUser() {
    var response = stub.getUserById(
        com.zextras.carbonio.user_management.sdk.grpc.GetUserByIdRequest.newBuilder()
            .setUserId(testUserId)
            .build());
    assertEquals(TEST_USER_EMAIL, response.getUser().getEmail());
    assertEquals(testUserId, response.getUser().getUserId());
  }

  @Test
  void getUserByIdNotFoundReturnsNotFound() {
    StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
        stub.getUserById(
            com.zextras.carbonio.user_management.sdk.grpc.GetUserByIdRequest.newBuilder()
                .setUserId("non-existent-id")
                .build()));
    assertEquals(Status.Code.NOT_FOUND, e.getStatus().getCode());
  }

  @Test
  void getUserByEmailReturnsUser() {
    var response = stub.getUserByEmail(
        com.zextras.carbonio.user_management.sdk.grpc.GetUserByEmailRequest.newBuilder()
            .setUserEmail(TEST_USER_EMAIL)
            .build());
    assertEquals(TEST_USER_EMAIL, response.getUser().getEmail());
    assertEquals(testUserId, response.getUser().getUserId());
  }

  @Test
  void getUserByEmailNotFoundReturnsNotFound() {
    StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
        stub.getUserByEmail(
            com.zextras.carbonio.user_management.sdk.grpc.GetUserByEmailRequest.newBuilder()
                .setUserEmail("nobody@carbonio.localhost")
                .build()));
    assertEquals(Status.Code.NOT_FOUND, e.getStatus().getCode());
  }

  @Test
  void getUsersReturnsUsers() {
    var response = stub.getUsers(
        com.zextras.carbonio.user_management.sdk.grpc.GetUsersRequest.newBuilder()
            .addUserIds(testUserId)
            .build());
    assertEquals(1, response.getUsersCount());
    assertEquals(TEST_USER_EMAIL, response.getUsers(0).getEmail());
  }

  @Test
  void getUsersWithEmptyListReturnsEmpty() {
    var response = stub.getUsers(
        com.zextras.carbonio.user_management.sdk.grpc.GetUsersRequest.newBuilder()
            .build());
    assertEquals(0, response.getUsersCount());
  }

  @Test
  void getUsersExceedingMaxReturnsInvalidArgument() {
    var builder = com.zextras.carbonio.user_management.sdk.grpc.GetUsersRequest.newBuilder();
    for (int i = 0; i < 101; i++) {
      builder.addUserIds("fake-id-" + i);
    }
    StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
        stub.getUsers(builder.build()));
    assertEquals(Status.Code.INVALID_ARGUMENT, e.getStatus().getCode());
  }
}
