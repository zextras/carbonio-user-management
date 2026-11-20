// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management;

import io.restassured.RestAssured;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;

/**
 * Base class for integration tests against a real Carbonio mailbox stack.
 *
 * <p>Provides shared setup: RestAssured configuration, SOAP authentication for the test user, and
 * userId resolution. The userId is resolved directly from LDAP (via {@link
 * MailboxStackTestResource}) so that tests start with a cold application cache.
 *
 * <p>Concrete subclasses must be annotated with {@code @QuarkusIntegrationTest} and
 * {@code @WithTestResource(MailboxStackTestResource.class)}.
 */
public abstract class BaseIT {

  private static final Pattern AUTH_TOKEN_PATTERN =
      Pattern.compile("<authToken[^>]*>([^<]+)</authToken>");

  protected static final String TEST_USER_EMAIL = "test-user@carbonio.localhost";
  protected static final String TEST_PASSWORD = "test-password";

  protected static String authToken;
  protected static String testUserId;
  protected static int restPort;

  @BeforeAll
  static void setUp() throws Exception {
    // Configure RestAssured from test.url system property
    String testUrl = System.getProperty("test.url");
    if (testUrl != null) {
      URI uri = URI.create(testUrl);
      RestAssured.baseURI = uri.getScheme() + "://" + uri.getHost();
      RestAssured.port = uri.getPort();
      restPort = uri.getPort();
    }

    // Authenticate test user via SOAP (does not touch the application)
    authToken = soapAuthenticate(TEST_USER_EMAIL, TEST_PASSWORD);

    // UserId resolved from LDAP by MailboxStackTestResource — no application cache warming
    testUserId = MailboxStackTestResource.testUserId;
  }

  protected static String soapAuthenticate(String account, String password) throws Exception {
    String mailboxUrl = MailboxStackTestResource.mailboxBaseUrl;
    if (mailboxUrl == null) {
      throw new IllegalStateException(
          "Mailbox URL not available — is MailboxStackTestResource running?");
    }

    String soapRequest =
        String.format(
            """
            <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
              <soap:Body>
                <AuthRequest xmlns="urn:zimbraAccount">
                  <account by="name">%s</account>
                  <password>%s</password>
                </AuthRequest>
              </soap:Body>
            </soap:Envelope>\
            """,
            account, password);

    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request =
        HttpRequest.newBuilder()
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
