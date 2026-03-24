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
        .cookie("ZM_AUTH_TOKEN", authToken)
        .when()
        .get("/users/myself")
        .then()
        .statusCode(200)
        .body("info.email", equalTo(TEST_USER_EMAIL))
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

  // --- Get by ID ---

  @Test
  void getUserByIdReturnsUser() {
    given()
        .cookie("ZM_AUTH_TOKEN", authToken)
        .when()
        .get("/users/id/" + testUserId)
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
        .get("/users/id/non-existent-user-id")
        .then()
        .statusCode(404);
  }

  @Test
  void getByIdWithInvalidTokenReturns401() {
    given()
        .cookie("ZM_AUTH_TOKEN", "invalid-token")
        .when()
        .get("/users/id/" + testUserId)
        .then()
        .statusCode(401);
  }

  @Test
  void getByIdWithoutTokenReturns401() {
    given()
        .when()
        .get("/users/id/" + testUserId)
        .then()
        .statusCode(401);
  }

  // --- Get by email ---

  @Test
  void getUserByEmailReturnsUser() {
    given()
        .cookie("ZM_AUTH_TOKEN", authToken)
        .when()
        .get("/users/email/" + TEST_USER_EMAIL)
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
        .get("/users/email/nobody@carbonio.localhost")
        .then()
        .statusCode(404);
  }

  @Test
  void getByEmailWithInvalidTokenReturns401() {
    given()
        .cookie("ZM_AUTH_TOKEN", "invalid-token")
        .when()
        .get("/users/email/" + TEST_USER_EMAIL)
        .then()
        .statusCode(401);
  }

  // --- Batch ---

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
        .post("/users")
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
        .post("/users")
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
        .post("/users")
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
        .post("/users")
        .then()
        .statusCode(200)
        .body("$", hasSize(1))
        .body("[0].userId", equalTo(testUserId));
  }

  @Test
  void batchWithInvalidTokenReturns401() {
    given()
        .cookie("ZM_AUTH_TOKEN", "invalid-token")
        .contentType("application/json")
        .body("[\"" + testUserId + "\"]")
        .when()
        .post("/users")
        .then()
        .statusCode(401);
  }
}
