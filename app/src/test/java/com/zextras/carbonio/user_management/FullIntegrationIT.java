// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.RestAssured;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * End-to-end integration test against a real Carbonio mailbox stack.
 *
 * <p>The app runs as a separate process (uber-jar); all CDI beans are production beans
 * (no mocks). The {@link MailboxStackTestResource} starts the full Docker Compose stack and
 * provides connection properties to the application.
 *
 * <p>Run with: {@code mvn verify -Dskip.integration.tests=false}
 */
@QuarkusIntegrationTest
@WithTestResource(MailboxStackTestResource.class)
class FullIntegrationIT {

  private static final Pattern AUTH_TOKEN_PATTERN =
      Pattern.compile("<authToken[^>]*>([^<]+)</authToken>");

  private static final String TEST_USER = "test-user@carbonio.localhost";
  private static final String TEST_PASSWORD = "test-password";

  private static String authToken;
  private static String testUserId;

  @BeforeAll
  static void authenticate() throws Exception {
    // Quarkus sets test.url after launching the uber-jar, but RestAssured.port is only
    // configured in beforeEach (too late for @BeforeAll). Read it from the system property.
    String testUrl = System.getProperty("test.url");
    if (testUrl != null) {
      URI uri = URI.create(testUrl);
      RestAssured.baseURI = uri.getScheme() + "://" + uri.getHost();
      RestAssured.port = uri.getPort();
    }

    authToken = soapAuthenticate(TEST_USER, TEST_PASSWORD);

    // Fetch userId for use in subsequent tests
    testUserId = given()
        .cookie("ZM_AUTH_TOKEN", authToken)
        .when()
        .get("/users/myself")
        .then()
        .statusCode(200)
        .extract()
        .path("info.userId");
  }

  // --- Authentication tests ---

  @Test
  void getMyselfReturnsAuthenticatedUser() {
    given()
        .cookie("ZM_AUTH_TOKEN", authToken)
        .when()
        .get("/users/myself")
        .then()
        .statusCode(200)
        .body("info.email", equalTo(TEST_USER))
        .body("info.userId", equalTo(testUserId))
        .body("locale", notNullValue());
  }

  @Test
  void getMyselfWithInvalidTokenReturns401() {
    given()
        .cookie("ZM_AUTH_TOKEN", "invalid-token-that-does-not-exist")
        .when()
        .get("/users/myself")
        .then()
        .statusCode(401);
  }

  @Test
  void getMyselfWithoutTokenReturns401() {
    given()
        .when()
        .get("/users/myself")
        .then()
        .statusCode(401);
  }

  // --- Cache flow tests (L2 Caffeine → L1 Postgres → SOAP, all real) ---

  @Test
  void repeatedGetMyselfReturnsSameData() {
    // First call populates caches, second call hits L2/L1
    for (int i = 0; i < 2; i++) {
      given()
          .cookie("ZM_AUTH_TOKEN", authToken)
          .when()
          .get("/users/myself")
          .then()
          .statusCode(200)
          .body("info.email", equalTo(TEST_USER))
          .body("info.userId", equalTo(testUserId));
    }
  }

  @Test
  void getUserByIdReturnsUser() {
    given()
        .cookie("ZM_AUTH_TOKEN", authToken)
        .when()
        .get("/users/id/" + testUserId)
        .then()
        .statusCode(200)
        .body("email", equalTo(TEST_USER))
        .body("userId", equalTo(testUserId));
  }

  @Test
  void getUserByEmailReturnsUser() {
    given()
        .cookie("ZM_AUTH_TOKEN", authToken)
        .when()
        .get("/users/email/" + TEST_USER)
        .then()
        .statusCode(200)
        .body("email", equalTo(TEST_USER))
        .body("userId", equalTo(testUserId));
  }

  @Test
  void getUsersBatchReturnsUsers() {
    given()
        .cookie("ZM_AUTH_TOKEN", authToken)
        .contentType("application/json")
        .body("[\"" + testUserId + "\"]")
        .when()
        .post("/users")
        .then()
        .statusCode(200)
        .body("[0].email", equalTo(TEST_USER))
        .body("[0].userId", equalTo(testUserId));
  }

  @Test
  void getUserByIdNotFoundReturns404() {
    given()
        .cookie("ZM_AUTH_TOKEN", authToken)
        .when()
        .get("/users/id/non-existent-user-id")
        .then()
        .statusCode(404);
  }

  @Test
  void getUserByEmailNotFoundReturns404() {
    given()
        .cookie("ZM_AUTH_TOKEN", authToken)
        .when()
        .get("/users/email/nobody@carbonio.localhost")
        .then()
        .statusCode(404);
  }

  /**
   * Authenticates against Carbonio mailbox via SOAP and returns the auth token.
   */
  private static String soapAuthenticate(String account, String password) throws Exception {
    String mailboxUrl = MailboxStackTestResource.mailboxBaseUrl;
    if (mailboxUrl == null) {
      throw new IllegalStateException("Mailbox URL not available — is MailboxStackTestResource running?");
    }

    String soapRequest = String.format("""
        <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
          <soap:Body>
            <AuthRequest xmlns="urn:zimbraAccount">
              <account by="name">%s</account>
              <password>%s</password>
            </AuthRequest>
          </soap:Body>
        </soap:Envelope>""", account, password);

    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(mailboxUrl + "/service/soap/AuthRequest"))
        .header("Content-Type", "application/soap+xml; charset=utf-8")
        .POST(HttpRequest.BodyPublishers.ofString(soapRequest))
        .build();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() != 200) {
      throw new IllegalStateException(
          "SOAP auth failed with status " + response.statusCode() + ": " + response.body());
    }

    Matcher matcher = AUTH_TOKEN_PATTERN.matcher(response.body());
    if (!matcher.find()) {
      throw new IllegalStateException("No authToken in SOAP response: " + response.body());
    }

    return matcher.group(1);
  }
}
