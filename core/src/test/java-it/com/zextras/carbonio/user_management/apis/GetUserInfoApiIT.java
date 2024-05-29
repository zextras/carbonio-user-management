// SPDX-FileCopyrightText: 2023 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.apis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zextras.carbonio.user_management.Simulator;
import com.zextras.carbonio.user_management.Simulator.SimulatorBuilder;
import com.zextras.carbonio.user_management.SoapHttpUtils;
import com.zextras.carbonio.user_management.generated.model.Status;
import com.zextras.carbonio.user_management.generated.model.UserInfo;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.HttpTester;
import org.eclipse.jetty.http.HttpTester.Response;
import org.eclipse.jetty.server.LocalConnector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GetUserInfoApiIT {

  private static Simulator simulator;

  @BeforeAll
  static void init() {
    simulator = SimulatorBuilder.aSimulator().init().withMailboxService().build().start();
  }

  @AfterAll
  static void cleanUpAll() {
    simulator.close();
  }

  @Test
  void givenAnExistingUserIdTheGetUserByIdApiShouldReturnTheRequestedUserInfo() throws Exception {
    // Given
    SoapHttpUtils soapHttpUtils = simulator.getSoapHttpUtils();
    MockServerClient mailboxServiceMock = simulator.getMailboxServiceMock();

    mailboxServiceMock
        .when(
            HttpRequest.request()
                .withMethod(HttpMethod.POST.toString())
                .withPath("/service/soap/")
                .withBody(
                    soapHttpUtils.getAccountInfoRequest(
                        "fake-token", "a28fdb4d-9f4b-4c7f-a572-43cef33f1d8b")))
        .respond(
            HttpResponse.response()
                .withStatusCode(HttpStatus.OK_200)
                .withBody(
                    soapHttpUtils.getAccountInfoResponse(
                        "a28fdb4d-9f4b-4c7f-a572-43cef33f1d8b",
                        "fake@example.com",
                        "example.com",
                        "Fake Account",
                        "active")));

    LocalConnector localConnector = simulator.getHttpLocalConnector();
    HttpTester.Request request = HttpTester.newRequest();
    request.setMethod(HttpMethod.GET.toString());
    request.setHeader(HttpHeader.HOST.toString(), "test");
    request.setHeader(HttpHeader.COOKIE.toString(), "ZM_AUTH_TOKEN=fake-token");
    request.setURI(("/users/id/a28fdb4d-9f4b-4c7f-a572-43cef33f1d8b"));

    // When
    Response response =
        HttpTester.parseResponse(HttpTester.from(localConnector.getResponse(request.generate())));

    // Then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.OK_200);

    UserInfo userInfo = new ObjectMapper().readValue(response.getContent(), UserInfo.class);

    assertThat(userInfo.getId().getUserId()).isEqualTo("a28fdb4d-9f4b-4c7f-a572-43cef33f1d8b");
    assertThat(userInfo.getEmail()).isEqualTo("fake@example.com");
    assertThat(userInfo.getFullName()).isEqualTo("Fake Account");
    assertThat(userInfo.getDomain()).isEqualTo("example.com");
    assertThat(userInfo.getStatus()).isEqualTo(Status.ACTIVE);
  }

  @Test
  void givenAnExistingUserEmailTheGetUserByEmailApiShouldReturnTheRequestedUserInfo()
      throws Exception {
    // Given
    SoapHttpUtils soapHttpUtils = simulator.getSoapHttpUtils();
    MockServerClient mailboxServiceMock = simulator.getMailboxServiceMock();

    mailboxServiceMock
        .when(
            HttpRequest.request()
                .withMethod(HttpMethod.POST.toString())
                .withPath("/service/soap/")
                .withBody(
                    soapHttpUtils.getAccountInfoRequest(
                        "fake-token", "a28fdb4d-9f4b-4c7f-a572-43cef33f1d8b")))
        .respond(
            HttpResponse.response()
                .withStatusCode(HttpStatus.OK_200)
                .withBody(
                    soapHttpUtils.getAccountInfoResponse(
                        "a28fdb4d-9f4b-4c7f-a572-43cef33f1d8b",
                        "fake@example.com",
                        "example.com",
                        "Fake Account",
                        "active")));

    LocalConnector localConnector = simulator.getHttpLocalConnector();
    HttpTester.Request request = HttpTester.newRequest();
    request.setMethod(HttpMethod.GET.toString());
    request.setHeader(HttpHeader.HOST.toString(), "test");
    request.setHeader(HttpHeader.COOKIE.toString(), "ZM_AUTH_TOKEN=fake-token");
    request.setURI(("/users/email/fake@example.com"));

    // When
    Response response =
        HttpTester.parseResponse(HttpTester.from(localConnector.getResponse(request.generate())));

    // Then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.OK_200);

    UserInfo userInfo = new ObjectMapper().readValue(response.getContent(), UserInfo.class);

    assertThat(userInfo.getId().getUserId()).isEqualTo("a28fdb4d-9f4b-4c7f-a572-43cef33f1d8b");
    assertThat(userInfo.getEmail()).isEqualTo("fake@example.com");
    assertThat(userInfo.getFullName()).isEqualTo("Fake Account");
    assertThat(userInfo.getDomain()).isEqualTo("example.com");
    assertThat(userInfo.getStatus()).isEqualTo(Status.ACTIVE);
  }

  @Test
  void givenAnExistingUserIdTheGetUsersApiShouldReturnTheRequestedListOfUserInfo()
      throws Exception {
    // Given
    SoapHttpUtils soapHttpUtils = simulator.getSoapHttpUtils();
    MockServerClient mailboxServiceMock = simulator.getMailboxServiceMock();

    mailboxServiceMock
        .when(
            HttpRequest.request()
                .withMethod(HttpMethod.POST.toString())
                .withPath("/service/soap/")
                .withBody(
                    soapHttpUtils.getAccountInfoRequest(
                        "fake-token", "a28fdb4d-9f4b-4c7f-a572-43cef33f1d8b")))
        .respond(
            HttpResponse.response()
                .withStatusCode(HttpStatus.OK_200)
                .withBody(
                    soapHttpUtils.getAccountInfoResponse(
                        "a28fdb4d-9f4b-4c7f-a572-43cef33f1d8b",
                        "fake@example.com",
                        "example.com",
                        "Fake Account",
                        "active")));

    LocalConnector localConnector = simulator.getHttpLocalConnector();
    HttpTester.Request request = HttpTester.newRequest();
    request.setMethod(HttpMethod.GET.toString());
    request.setHeader(HttpHeader.HOST.toString(), "test");
    request.setHeader(HttpHeader.COOKIE.toString(), "ZM_AUTH_TOKEN=fake-token");
    String[] userIds = {"a28fdb4d-9f4b-4c7f-a572-43cef33f1d8b"};
    String userIdsQueryParam = String.join(",", userIds);
    request.setURI("/users?userIds=" + userIdsQueryParam);

    // When
    Response response =
        HttpTester.parseResponse(HttpTester.from(localConnector.getResponse(request.generate())));

    // Then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.OK_200);
    List<UserInfo> userInfoList =
        new ObjectMapper().readValue(response.getContent(), new TypeReference<List<UserInfo>>() {});
    UserInfo userInfo = userInfoList.get(0);

    assertThat(userInfo.getId().getUserId()).isEqualTo("a28fdb4d-9f4b-4c7f-a572-43cef33f1d8b");
    assertThat(userInfo.getEmail()).isEqualTo("fake@example.com");
    assertThat(userInfo.getFullName()).isEqualTo("Fake Account");
    assertThat(userInfo.getDomain()).isEqualTo("example.com");
    assertThat(userInfo.getStatus()).isEqualTo(Status.ACTIVE);
  }

  @Test
  void givenANotExistingUserIdTheGetUserByIdApiShouldReturnNotFound() throws Exception {
    // Given
    SoapHttpUtils soapHttpUtils = simulator.getSoapHttpUtils();
    MockServerClient mailboxServiceMock = simulator.getMailboxServiceMock();

    mailboxServiceMock
        .when(
            HttpRequest.request()
                .withMethod(HttpMethod.POST.toString())
                .withPath("/service/soap/")
                .withBody(
                    soapHttpUtils.getAccountInfoRequest(
                        "fake-token", "2fe6fedd-f640-40c8-bd8e-6b60a040776b")))
        .respond(
            HttpResponse.response()
                .withStatusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .withBody(soapHttpUtils.getSoapNotFoundErrorResponse()));

    LocalConnector localConnector = simulator.getHttpLocalConnector();
    HttpTester.Request request = HttpTester.newRequest();
    request.setMethod(HttpMethod.GET.toString());
    request.setHeader(HttpHeader.HOST.toString(), "test");
    request.setHeader(HttpHeader.COOKIE.toString(), "ZM_AUTH_TOKEN=fake-token");
    request.setURI(("/users/id/2fe6fedd-f640-40c8-bd8e-6b60a040776b"));

    // When
    Response response =
        HttpTester.parseResponse(HttpTester.from(localConnector.getResponse(request.generate())));

    // Then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND_404);
  }
}
