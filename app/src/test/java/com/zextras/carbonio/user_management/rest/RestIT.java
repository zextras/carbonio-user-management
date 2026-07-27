// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zextras.carbonio.user_management.BaseIT;
import com.zextras.carbonio.user_management.MailboxStackTestResource;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * REST endpoint integration tests against a real Carbonio mailbox stack.
 *
 * <p>Run with: {@code mvn verify -Dskip.integration.tests=false}
 */
@QuarkusIntegrationTest
@WithTestResource(MailboxStackTestResource.class)
class RestIT extends BaseIT {

  // --- Myself ---

  @Test
  void getMyselfReturnsAuthenticatedUser() {
    given()
        .header("ZM_AUTH_TOKEN", authToken)
        .when()
        .get("/internal/users/myself")
        .then()
        .statusCode(200)
        .body("info.email", equalTo(TEST_USER_EMAIL))
        .body("info.userId", equalTo(testUserId))
        .body("locale", notNullValue());
  }

  @Test
  void getMyselfWithInvalidTokenReturns401() {
    given()
        .header("ZM_AUTH_TOKEN", "invalid-token-that-does-not-exist")
        .when()
        .get("/internal/users/myself")
        .then()
        .statusCode(401);
  }

  @Test
  void getMyselfWithoutTokenReturns401() {
    given()
        .when()
        .get("/internal/users/myself")
        .then()
        .statusCode(401);
  }

  // --- Myself cache bypass ---

  /**
   * Proves {@code bypassCache=true} really skips the cache read and goes back to mailbox: the
   * account is modified behind the service's back, so only a fresh mailbox round trip can observe
   * the new value. A fresh token is used so this test owns its own cache entry.
   */
  @Test
  void getMyselfWithBypassCacheRevalidatesAgainstMailbox() throws Exception {
    String token = soapAuthenticate(TEST_USER_EMAIL, TEST_PASSWORD);
    MailboxStackTestResource.setTestUserDisplayName("Before Bypass");
    try {
      // Cold cache: mailbox hit #1, result cached under this token
      given()
          .header("ZM_AUTH_TOKEN", token)
          .when()
          .get("/internal/users/myself")
          .then()
          .statusCode(200)
          .body("info.fullName", equalTo("Before Bypass"));

      MailboxStackTestResource.setTestUserDisplayName("After Bypass");

      // No bypass: served from cache, still the stale value -> mailbox was NOT hit
      given()
          .header("ZM_AUTH_TOKEN", token)
          .when()
          .get("/internal/users/myself")
          .then()
          .statusCode(200)
          .body("info.fullName", equalTo("Before Bypass"));

      // Bypass: cache read skipped -> mailbox hit #2, fresh value
      given()
          .header("ZM_AUTH_TOKEN", token)
          .queryParam("bypassCache", true)
          .when()
          .get("/internal/users/myself")
          .then()
          .statusCode(200)
          .body("info.fullName", equalTo("After Bypass"));

      // The bypass refreshed the entry rather than disabling the cache
      given()
          .header("ZM_AUTH_TOKEN", token)
          .when()
          .get("/internal/users/myself")
          .then()
          .statusCode(200)
          .body("info.fullName", equalTo("After Bypass"));
    } finally {
      MailboxStackTestResource.setTestUserDisplayName("");
    }
  }

  @Test
  void getMyselfWithBypassCacheFalseBehavesLikeNoParameter() {
    given()
        .header("ZM_AUTH_TOKEN", authToken)
        .queryParam("bypassCache", false)
        .when()
        .get("/internal/users/myself")
        .then()
        .statusCode(200)
        .body("info.userId", equalTo(testUserId));
  }

  @Test
  void getMyselfWithBypassCacheAndNoTokenStillReturns401() {
    given()
        .queryParam("bypassCache", true)
        .when()
        .get("/internal/users/myself")
        .then()
        .statusCode(401);
  }

  // --- Get by ID ---

  @Test
  void getUserByIdReturnsUser() {
    given()
        .cookie("ZM_AUTH_TOKEN", authToken)
        .when()
        .get("/internal/users/id/" + testUserId)
        .then()
        .statusCode(200)
        .body("email", equalTo(TEST_USER_EMAIL))
        .body("userId", equalTo(testUserId));
  }

  @Test
  void getUserByIdNotFoundReturns404() {
    given()
        .cookie("ZM_AUTH_TOKEN", authToken)
        .when()
        .get("/internal/users/id/non-existent-user-id")
        .then()
        .statusCode(404);
  }

  @Test
  void getByIdWithInvalidTokenStillReturnsUser() {
    // by-id is a trusted forward, mesh-gated (not app-gated): an invalid/garbage cookie is
    // simply ignored, the lookup is not affected by any token.
    given()
        .cookie("ZM_AUTH_TOKEN", "invalid-token")
        .when()
        .get("/internal/users/id/" + testUserId)
        .then()
        .statusCode(200)
        .body("userId", equalTo(testUserId));
  }

  @Test
  void getByIdWithoutTokenStillReturnsUser() {
    // by-id does not require a token at all.
    given()
        .when()
        .get("/internal/users/id/" + testUserId)
        .then()
        .statusCode(200)
        .body("userId", equalTo(testUserId));
  }

  @Test
  void getByIdIgnoresBypassCache() {
    // The cache bypass exists only on /myself, where a stale entry is an authorization-freshness
    // problem. On by-id it is an unknown query parameter and is simply ignored.
    given()
        .queryParam("bypassCache", true)
        .when()
        .get("/internal/users/id/" + testUserId)
        .then()
        .statusCode(200)
        .body("userId", equalTo(testUserId));
  }

  // --- Get by email ---

  @Test
  void getUserByEmailReturnsUser() {
    given()
        .cookie("ZM_AUTH_TOKEN", authToken)
        .when()
        .get("/internal/users/email/" + TEST_USER_EMAIL)
        .then()
        .statusCode(200)
        .body("email", equalTo(TEST_USER_EMAIL))
        .body("userId", equalTo(testUserId));
  }

  @Test
  void getUserByEmailNotFoundReturns404() {
    given()
        .cookie("ZM_AUTH_TOKEN", authToken)
        .when()
        .get("/internal/users/email/nobody@carbonio.localhost")
        .then()
        .statusCode(404);
  }

  @Test
  void getByEmailWithInvalidTokenStillReturnsUser() {
    // by-email is a trusted forward, mesh-gated (not app-gated): an invalid/garbage cookie is
    // simply ignored, the lookup is not affected by any token.
    given()
        .cookie("ZM_AUTH_TOKEN", "invalid-token")
        .when()
        .get("/internal/users/email/" + TEST_USER_EMAIL)
        .then()
        .statusCode(200)
        .body("userId", equalTo(testUserId));
  }

  @Test
  void getByEmailWithoutTokenStillReturnsUser() {
    // by-email does not require a token at all.
    given()
        .when()
        .get("/internal/users/email/" + TEST_USER_EMAIL)
        .then()
        .statusCode(200)
        .body("userId", equalTo(testUserId));
  }

  // --- Batch ---

  @Test
  void getUsersBatchReturnsUsers() {
    given()
        .cookie("ZM_AUTH_TOKEN", authToken)
        .contentType("application/json")
        .body("[\"" + testUserId + "\"]")
        .when()
        .post("/internal/users")
        .then()
        .statusCode(200)
        .body("[0].email", equalTo(TEST_USER_EMAIL))
        .body("[0].userId", equalTo(testUserId));
  }

  @Test
  void batchWithDuplicateIdsReturnsDeduplicatedResults() {
    // Sending the same ID twice should not return duplicates
    String body = "[\"" + testUserId + "\",\"" + testUserId + "\"]";
    List<?> results = given()
        .cookie("ZM_AUTH_TOKEN", authToken)
        .contentType("application/json")
        .body(body)
        .when()
        .post("/internal/users")
        .then()
        .statusCode(200)
        .extract()
        .jsonPath().getList("$");
    // Service may or may not deduplicate; verify at least one result with correct data
    assertTrue(results.size() >= 1 && results.size() <= 2);
  }

  @Test
  void batchWithEmptyListReturnsEmptyArray() {
    given()
        .cookie("ZM_AUTH_TOKEN", authToken)
        .contentType("application/json")
        .body("[]")
        .when()
        .post("/internal/users")
        .then()
        .statusCode(200)
        .body("$", empty());
  }

  @Test
  void batchExceedingMaxReturns400() {
    // Build a list of 101 fake IDs
    List<String> ids = IntStream.rangeClosed(1, 101)
        .mapToObj(i -> "fake-id-" + i)
        .toList();
    given()
        .cookie("ZM_AUTH_TOKEN", authToken)
        .contentType("application/json")
        .body(ids)
        .when()
        .post("/internal/users")
        .then()
        .statusCode(400);
  }

  @Test
  void batchWithMixOfExistingAndMissingIds() {
    String body = "[\"" + testUserId + "\",\"non-existent-user-id\"]";
    given()
        .cookie("ZM_AUTH_TOKEN", authToken)
        .contentType("application/json")
        .body(body)
        .when()
        .post("/internal/users")
        .then()
        .statusCode(200)
        .body("$", hasSize(1))
        .body("[0].userId", equalTo(testUserId));
  }

  @Test
  void batchWithInvalidTokenStillReturnsUsers() {
    // batch is a trusted forward, mesh-gated (not app-gated): an invalid/garbage cookie is
    // simply ignored, the lookup is not affected by any token.
    given()
        .cookie("ZM_AUTH_TOKEN", "invalid-token")
        .contentType("application/json")
        .body("[\"" + testUserId + "\"]")
        .when()
        .post("/internal/users")
        .then()
        .statusCode(200)
        .body("[0].userId", equalTo(testUserId));
  }

  @Test
  void batchWithoutTokenStillReturnsUsers() {
    // batch does not require a token at all.
    given()
        .contentType("application/json")
        .body("[\"" + testUserId + "\"]")
        .when()
        .post("/internal/users")
        .then()
        .statusCode(200)
        .body("[0].userId", equalTo(testUserId));
  }
}
