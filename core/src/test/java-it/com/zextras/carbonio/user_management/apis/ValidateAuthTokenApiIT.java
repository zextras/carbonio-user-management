// SPDX-FileCopyrightText: 2024 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.apis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zextras.carbonio.user_management.Simulator;
import com.zextras.carbonio.user_management.SoapHttpUtils;
import com.zextras.carbonio.user_management.Simulator.SimulatorBuilder;

import com.zextras.carbonio.user_management.generated.model.UserId;
import java.util.List;
import org.assertj.core.api.Assertions;
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

public class ValidateAuthTokenApiIT {

  private static Simulator simulator;
  private static List<String>  getInfoRequestSections;

  @BeforeAll
  static void init() throws Exception {
    simulator = SimulatorBuilder.aSimulator().init().withMailboxService().build().start();
    getInfoRequestSections = List.of("children");
  }

  @AfterAll
  static void cleanUpAl() throws Exception {
    simulator.stopAll();
  }

  @Test
  void givenAValidAuthTokenTheValidateTokenApiShouldReturnTheRelatedUserId() throws Exception {
    // Given
    SoapHttpUtils soapHttpUtils = simulator.getSoapHttpUtils();
    MockServerClient mailboxServiceMock = simulator.getMailboxServiceMock();
    mailboxServiceMock
        .when(
            HttpRequest.request()
                .withMethod(HttpMethod.POST.toString())
                .withPath("/service/soap/")
                .withBody(soapHttpUtils.getInfoRequest(getInfoRequestSections, "fake-token")))
        .respond(
            HttpResponse.response()
                .withStatusCode(HttpStatus.OK_200)
                .withBody(
                    soapHttpUtils.getInfoResponse(
                        "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                        "fake@example.com",
                        "example.com",
                        "Fake Account",
                        "it_IT",
                        "TRUE")));

    LocalConnector localConnector = simulator.getHttpLocalConnector();
    HttpTester.Request request = HttpTester.newRequest();
    request.setMethod(HttpMethod.GET.toString());
    request.setHeader(HttpHeader.HOST.toString(), "test");
    request.setURI(("/auth/token/fake-token"));

    // When
    Response response = HttpTester.parseResponse(HttpTester.from(localConnector.getResponse(request.generate())));

    // Then
    Assertions.assertThat(response.getStatus()).isEqualTo(HttpStatus.OK_200);
    UserId userId = new ObjectMapper().readValue(response.getContent(), UserId.class);
    Assertions.assertThat(userId.getUserId()).isEqualTo("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  }

  @Test
  void givenAValidAuthTokenTheValidateTokenApiCalledTwiceShouldReturnTheUserIdCallingTheMailboxOnlyOnce()
    throws Exception {
    // Given
    SoapHttpUtils soapHttpUtils = simulator.getSoapHttpUtils();
    MockServerClient mailboxServiceMock = simulator.getMailboxServiceMock();
    mailboxServiceMock
      .when(
        HttpRequest.request()
          .withMethod(HttpMethod.POST.toString())
          .withPath("/service/soap/")
          .withBody(soapHttpUtils.getInfoRequest(getInfoRequestSections, "fake-token")))
      .respond(
        HttpResponse.response()
          .withStatusCode(HttpStatus.OK_200)
          .withBody(
            soapHttpUtils.getInfoResponse(
              "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
              "fake@example.com",
              "example.com",
              "Fake Account",
              "it_IT",
              "TRUE")));

    LocalConnector localConnector = simulator.getHttpLocalConnector();
    HttpTester.Request request = HttpTester.newRequest();
    request.setMethod(HttpMethod.GET.toString());
    request.setHeader(HttpHeader.HOST.toString(), "test");
    request.setURI(("/auth/token/fake-token"));
    // First call to fetch the account and save it in cache
    localConnector.getResponse(request.generate());

    // Reset the mailboxServiceMock to be sure the system does not call the mailbox the second time
    // because the information is already cached
    mailboxServiceMock.reset();

    // When
    Response response = HttpTester.parseResponse(HttpTester.from(localConnector.getResponse(request.generate())));

    // Then
    Assertions.assertThat(response.getStatus()).isEqualTo(HttpStatus.OK_200);
    UserId userId = new ObjectMapper().readValue(response.getContent(), UserId.class);
    Assertions.assertThat(userId.getUserId()).isEqualTo("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    mailboxServiceMock.verifyZeroInteractions(); // After the reset
  }
}
