// SPDX-FileCopyrightText: 2023 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.apis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zextras.carbonio.user_management.Simulator;
import com.zextras.carbonio.user_management.Simulator.SimulatorBuilder;
import com.zextras.carbonio.user_management.SoapHttpUtils;
import com.zextras.carbonio.user_management.generated.model.Locale;
import com.zextras.carbonio.user_management.generated.model.UserMyself;
import org.assertj.core.api.Assertions;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.HttpTester;
import org.eclipse.jetty.http.HttpTester.Response;
import org.eclipse.jetty.server.LocalConnector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

public class GetMyselfByCookieApiIT {

  static Simulator simulator;

  @BeforeAll
  static void init() {
    simulator = SimulatorBuilder
      .aSimulator()
      .init()
      .withMailboxService()
      .build()
      .start();
  }

  @BeforeEach
  void setup() {
    simulator.resetAll();
    simulator.setupWsdl();
  }

  @AfterAll
  static void cleanUpAll() {
    simulator.close();
  }

  @Test
  void givenAValidUserCookieTheGetUserMyselfApiShouldReturnTheRequesterFullInfo() throws Exception {
    // Given
    SoapHttpUtils soapHttpUtils = simulator.getSoapHttpUtils();
    MockServerClient mailboxServiceMock = simulator.getMailboxServiceMock();

    mailboxServiceMock
      .when(HttpRequest
        .request()
        .withMethod(HttpMethod.POST.toString())
        .withPath("/service/soap/")
        .withBody(
          soapHttpUtils.getInfoRequest("valid-token"))
      )
      .respond(HttpResponse
        .response()
        .withStatusCode(HttpStatus.OK_200)
        .withBody(
          soapHttpUtils.getInfoResponse(
            "a28fdb4d-9f4b-4c7f-a572-43cef33f1d8b",
            "fake@example.com",
            "example.com",
            "Fake Account",
            "en"
          )
        ));

    LocalConnector httpLocalConnector = simulator.getHttpLocalConnector();
    HttpTester.Request request = HttpTester.newRequest();
    request.setMethod(HttpMethod.GET.toString());
    request.setHeader(HttpHeader.HOST.toString(), "test");
    request.setHeader(HttpHeader.COOKIE.toString(), "ZM_AUTH_TOKEN=valid-token");
    request.setURI(("/users/myself"));

    // When
    Response response =
      HttpTester.parseResponse(HttpTester.from(httpLocalConnector.getResponse(request.generate())));

    // Then
    Assertions.assertThat(response.getStatus()).isEqualTo(HttpStatus.OK_200);

    UserMyself userMyself = new ObjectMapper().readValue(response.getContent(), UserMyself.class);
    Assertions.assertThat(userMyself.getId().getUserId())
      .isEqualTo("a28fdb4d-9f4b-4c7f-a572-43cef33f1d8b");
    Assertions.assertThat(userMyself.getEmail()).isEqualTo("fake@example.com");
    Assertions.assertThat(userMyself.getDomain()).isEqualTo("example.com");
    Assertions.assertThat(userMyself.getFullName()).isEqualTo("Fake Account");
    Assertions.assertThat(userMyself.getLocale()).isEqualTo(Locale.EN);
  }

  @Test
  void givenAnInvalidUserCookieTheGetUserMyselfApiShouldReturnANotFoundResponse() throws Exception {
    // Given
    SoapHttpUtils soapHttpUtils = simulator.getSoapHttpUtils();
    MockServerClient mailboxServiceMock = simulator.getMailboxServiceMock();

    mailboxServiceMock
      .when(HttpRequest
        .request()
        .withMethod(HttpMethod.POST.toString())
        .withPath("/service/soap/")
        .withBody(
          soapHttpUtils.getInfoRequest("invalid-token"))
      )
      .respond(HttpResponse
        .response()
        .withStatusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
        .withBody(soapHttpUtils.getSoapNotFoundErrorResponse())
      );

    LocalConnector httpLocalConnector = simulator.getHttpLocalConnector();
    HttpTester.Request request = HttpTester.newRequest();
    request.setMethod(HttpMethod.GET.toString());
    request.setHeader(HttpHeader.HOST.toString(), "test");
    request.setHeader(HttpHeader.COOKIE.toString(), "ZM_AUTH_TOKEN=invalid-token");
    request.setURI(("/users/myself"));

    // When
    Response response = HttpTester
      .parseResponse(HttpTester.from(httpLocalConnector.getResponse(request.generate())));

    // Then
    Assertions.assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND_404);
    Assertions.assertThat(response.getContent()).hasSize(0);
  }

  @Test
  void givenAValidUserCookieAndAnInvalidResponseFromTheMailboxTheGetUserMyselfApiShouldReturnANotFoundResponse()
    throws Exception {
    // Given
    SoapHttpUtils soapHttpUtils = simulator.getSoapHttpUtils();
    MockServerClient mailboxServiceMock = simulator.getMailboxServiceMock();

    mailboxServiceMock
      .when(HttpRequest
        .request()
        .withMethod(HttpMethod.POST.toString())
        .withPath("/service/soap/")
        .withBody(
          soapHttpUtils.getInfoRequest("valid-token"))
      )
      .respond(HttpResponse
        .response()
        .withStatusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
        .withBody("wrong response")
      );

    LocalConnector httpLocalConnector = simulator.getHttpLocalConnector();
    HttpTester.Request request = HttpTester.newRequest();
    request.setMethod(HttpMethod.GET.toString());
    request.setHeader(HttpHeader.HOST.toString(), "test");
    request.setHeader(HttpHeader.COOKIE.toString(), "ZM_AUTH_TOKEN=valid-token");
    request.setURI(("/users/myself"));

    // When
    Response response = HttpTester
      .parseResponse(HttpTester.from(httpLocalConnector.getResponse(request.generate())));

    // Then
    Assertions.assertThat(response.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR_500);
    Assertions
      .assertThat(response.getContent())
      .hasSize(64)
      .isEqualTo("Unable to get account user info due to an internal service error");
  }
}
