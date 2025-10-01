// SPDX-FileCopyrightText: 2023 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.apis;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zextras.carbonio.user_management.Simulator;
import com.zextras.carbonio.user_management.Simulator.SimulatorBuilder;
import com.zextras.carbonio.user_management.SoapHttpUtils;
import com.zextras.carbonio.user_management.generated.model.UserInfo;
import com.zextras.carbonio.user_management.generated.model.UserMyself;
import com.zextras.carbonio.user_management.generated.model.UserStatus;
import com.zextras.carbonio.user_management.generated.model.UserType;
import java.util.List;
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

class GetUserInfoApiIT {

  private static Simulator simulator;

  @BeforeAll
  static void init() throws Exception {
    simulator = SimulatorBuilder.aSimulator().init().withMailboxService().build().start();
  }

  @AfterAll
  static void cleanUpAll() throws Exception {
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
                    soapHttpUtils.getAccountInfoRequestById(
                        "fake-token", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")))
        .respond(
            HttpResponse.response()
                .withStatusCode(HttpStatus.OK_200)
                .withBody(
                    soapHttpUtils.getAccountInfoResponse(
                        "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                        "fake@example.com",
                        "example.com",
                        "Fake Account",
                        "active",
                        "TRUE")));

    LocalConnector localConnector = simulator.getHttpLocalConnector();
    HttpTester.Request request = HttpTester.newRequest();
    request.setMethod(HttpMethod.GET.toString());
    request.setHeader(HttpHeader.HOST.toString(), "test");
    request.setHeader(HttpHeader.COOKIE.toString(), "ZM_AUTH_TOKEN=fake-token");
    request.setURI(("/users/id/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));

    // When
    Response response =
        HttpTester.parseResponse(HttpTester.from(localConnector.getResponse(request.generate())));

    // Then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.OK_200);

    UserInfo userInfo = new ObjectMapper().readValue(response.getContent(), UserInfo.class);

    assertThat(userInfo.getId().getUserId()).isEqualTo("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    assertThat(userInfo.getEmail()).isEqualTo("fake@example.com");
    assertThat(userInfo.getFullName()).isEqualTo("Fake Account");
    assertThat(userInfo.getDomain()).isEqualTo("example.com");
    assertThat(userInfo.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(userInfo.getType()).isEqualTo(UserType.GUEST);
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
                    soapHttpUtils.getAccountInfoRequestByEmail(
                        "fake-token", "accountemail@example.com")))
        .respond(
            HttpResponse.response()
                .withStatusCode(HttpStatus.OK_200)
                .withBody(
                    soapHttpUtils.getAccountInfoResponse(
                        "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                        "accountemail@example.com",
                        "example.com",
                        "Fake Account",
                        "active",
                        "FALSE")));

    LocalConnector localConnector = simulator.getHttpLocalConnector();
    HttpTester.Request request = HttpTester.newRequest();
    request.setMethod(HttpMethod.GET.toString());
    request.setHeader(HttpHeader.HOST.toString(), "test");
    request.setHeader(HttpHeader.COOKIE.toString(), "ZM_AUTH_TOKEN=fake-token");
    request.setURI(("/users/email/accountemail@example.com"));

    // When
    Response response =
        HttpTester.parseResponse(HttpTester.from(localConnector.getResponse(request.generate())));

    // Then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.OK_200);

    UserInfo userInfo = new ObjectMapper().readValue(response.getContent(), UserInfo.class);

    assertThat(userInfo.getId().getUserId()).isEqualTo("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    assertThat(userInfo.getEmail()).isEqualTo("accountemail@example.com");
    assertThat(userInfo.getFullName()).isEqualTo("Fake Account");
    assertThat(userInfo.getDomain()).isEqualTo("example.com");
    assertThat(userInfo.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(userInfo.getType()).isEqualTo(UserType.INTERNAL);
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
                    soapHttpUtils.getAccountInfoRequestById(
                        "fake-token", "cccccccc-cccc-cccc-cccc-cccccccccccc")))
        .respond(
            HttpResponse.response()
                .withStatusCode(HttpStatus.OK_200)
                .withBody(
                    soapHttpUtils.getAccountInfoResponse(
                        "cccccccc-cccc-cccc-cccc-cccccccccccc",
                        "fake@example.com",
                        "example.com",
                        "Fake Account",
                        "active",
                        "FALSE")));

    LocalConnector localConnector = simulator.getHttpLocalConnector();
    HttpTester.Request request = HttpTester.newRequest();
    request.setMethod(HttpMethod.GET.toString());
    request.setHeader(HttpHeader.HOST.toString(), "test");
    request.setHeader(HttpHeader.COOKIE.toString(), "ZM_AUTH_TOKEN=fake-token");
    String[] userIds = {"cccccccc-cccc-cccc-cccc-cccccccccccc"};
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

    assertThat(userInfo.getId().getUserId()).isEqualTo("cccccccc-cccc-cccc-cccc-cccccccccccc");
    assertThat(userInfo.getEmail()).isEqualTo("fake@example.com");
    assertThat(userInfo.getFullName()).isEqualTo("Fake Account");
    assertThat(userInfo.getDomain()).isEqualTo("example.com");
    assertThat(userInfo.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(userInfo.getType()).isEqualTo(UserType.INTERNAL);
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
                    soapHttpUtils.getAccountInfoRequestById(
                        "fake-token", "dddddddd-dddd-dddd-dddd-dddddddddddd")))
        .respond(
            HttpResponse.response()
                .withStatusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .withBody(soapHttpUtils.getSoapNotFoundErrorResponse()));

    LocalConnector localConnector = simulator.getHttpLocalConnector();
    HttpTester.Request request = HttpTester.newRequest();
    request.setMethod(HttpMethod.GET.toString());
    request.setHeader(HttpHeader.HOST.toString(), "test");
    request.setHeader(HttpHeader.COOKIE.toString(), "ZM_AUTH_TOKEN=fake-token");
    request.setURI(("/users/id/dddddddd-dddd-dddd-dddd-dddddddddddd"));

    // When
    Response response =
        HttpTester.parseResponse(HttpTester.from(localConnector.getResponse(request.generate())));

    // Then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND_404);
  }


}
