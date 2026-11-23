package com.zextras.carbonio.user_management.rights;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.zextras.carbonio.user_management.BaseIT;
import com.zextras.carbonio.user_management.MailboxStackTestResource;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.http.ContentType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Compares the rights decisions of User Management, computed from LDAP, with mailbox's own ACL
 * engine ({@code zmprov -l checkRight}) on the same directory.
 */
@QuarkusIntegrationTest
@WithTestResource(MailboxStackTestResource.class)
class RightsIT extends BaseIT {

  private static final String DOMAIN = "carbonio.localhost";
  private static final String OTHER_DOMAIN = "rights-other.localhost";
  private static final String GLOBAL = "rights-global@" + DOMAIN;
  private static final String USERS_ADMIN = "rights-users-admin@" + DOMAIN;
  private static final String QUOTA_ADMIN = "rights-quota-admin@" + DOMAIN;
  private static final String PLAIN = "rights-plain@" + DOMAIN;
  private static final String TARGET = "rights-target@" + DOMAIN;
  private static final String TARGET_WITH_DENY = "rights-target-deny@" + DOMAIN;
  private static final String OTHER_TARGET = "rights-target@" + OTHER_DOMAIN;
  private static final List<String> ACTORS = List.of(GLOBAL, USERS_ADMIN, QUOTA_ADMIN, PLAIN);
  private static final List<String> ACCOUNT_TARGETS =
      List.of(TARGET, TARGET_WITH_DENY, OTHER_TARGET, GLOBAL);
  private static final List<String> ACCOUNT_RIGHTS =
      List.of(
          "modifyAccount",
          "getAccount",
          "setDomainAdminAccountAndCalendarResourceAttrs",
          "domainAdminAccountRights",
          "getMailboxInfo",
          "deleteAccount",
          "configureQuota",
          "set.account.displayName",
          "set.account.zimbraMailQuota",
          "set.account.zimbraMailHost",
          "get.account.zimbraMailHost");
  private static final List<String> DOMAIN_RIGHTS = List.of("getDomain", "getDomainQuotaUsage");

  private static Map<String, String> ids;

  @BeforeAll
  static void provision() {
    MailboxStackTestResource.zmprov(
        false,
        String.join(
            "\n",
            "cd " + OTHER_DOMAIN,
            "ca " + GLOBAL + " test-password zimbraIsAdminAccount TRUE",
            "ca " + USERS_ADMIN + " test-password zimbraIsDelegatedAdminAccount TRUE",
            "ca " + QUOTA_ADMIN + " test-password zimbraIsDelegatedAdminAccount TRUE",
            "ca " + PLAIN + " test-password",
            "ca " + TARGET + " test-password",
            "ca " + TARGET_WITH_DENY + " test-password",
            "ca " + OTHER_TARGET + " test-password",
            "cdl rights-users-admins@" + DOMAIN + " zimbraIsAdminGroup TRUE",
            "cdl rights-delegated-admins@" + DOMAIN + " zimbraIsAdminGroup TRUE",
            "adlm rights-users-admins@" + DOMAIN + " " + USERS_ADMIN + " " + PLAIN,
            "adlm rights-users-admins@" + DOMAIN + " rights-delegated-admins@" + DOMAIN,
            "adlm rights-delegated-admins@" + DOMAIN + " " + QUOTA_ADMIN,
            "grr domain "
                + DOMAIN
                + " grp rights-users-admins@"
                + DOMAIN
                + " domainAdminAccountRights",
            "grr domain "
                + DOMAIN
                + " grp rights-delegated-admins@"
                + DOMAIN
                + " getDomainQuotaUsage",
            "grr domain " + DOMAIN + " grp rights-delegated-admins@" + DOMAIN + " configureQuota",
            "grr account " + TARGET_WITH_DENY + " usr " + USERS_ADMIN + " -set.account.displayName",
            "grr account " + OTHER_TARGET + " usr " + QUOTA_ADMIN + " set.account.zimbraMailHost"));
    ids =
        Map.of(
            GLOBAL, id(GLOBAL),
            USERS_ADMIN, id(USERS_ADMIN),
            QUOTA_ADMIN, id(QUOTA_ADMIN),
            PLAIN, id(PLAIN),
            TARGET, id(TARGET));
  }

  private record Case(String actor, String right, String targetType, String target) {
    String ckr() {
      return "ckr " + targetType + " " + target + " " + actor + " " + right;
    }
  }

  @Test
  void accountRightsMatchMailbox() {
    List<Case> cases = new ArrayList<>();
    for (String actor : ACTORS) {
      for (String target : ACCOUNT_TARGETS) {
        for (String right : ACCOUNT_RIGHTS) {
          cases.add(new Case(actor, right, "account", target));
        }
      }
    }
    assertThat(mismatches(cases)).isEmpty();
  }

  @Test
  void domainRightsMatchMailbox() {
    List<Case> cases = new ArrayList<>();
    for (String actor : ACTORS) {
      for (String domain : List.of(DOMAIN, OTHER_DOMAIN)) {
        for (String right : DOMAIN_RIGHTS) {
          cases.add(new Case(actor, right, "domain", domain));
        }
      }
    }
    assertThat(mismatches(cases)).isEmpty();
  }

  private static List<String> mismatches(List<Case> cases) {
    List<Boolean> expected = mailboxDecisions(cases);
    List<String> mismatches = new ArrayList<>();
    for (int i = 0; i < cases.size(); i++) {
      Case c = cases.get(i);
      boolean actual = umAllows(c.actor(), c.right(), c.targetType(), c.target());
      if (expected.get(i) != actual) {
        mismatches.add(c.ckr() + ": mailbox=" + expected.get(i) + " um=" + actual);
      }
    }
    return mismatches;
  }

  private static List<Boolean> mailboxDecisions(List<Case> cases) {
    String output =
        MailboxStackTestResource.zmprov(
            true, String.join("\n", cases.stream().map(Case::ckr).toList()));
    Map<String, Boolean> byCommand = new java.util.HashMap<>();
    for (String chunk : output.split("prov> ")) {
      List<String> lines = chunk.lines().map(String::trim).filter(l -> !l.isEmpty()).toList();
      if (lines.isEmpty() || !lines.getFirst().startsWith("ckr ")) {
        continue;
      }
      byCommand.put(lines.getFirst(), decision(lines.getFirst(), lines.subList(1, lines.size())));
    }
    return cases.stream()
        .map(
            c -> {
              Boolean decision = byCommand.get(c.ckr());
              if (decision == null) {
                throw new IllegalStateException(
                    "No mailbox decision for " + c.ckr() + ":\n" + output);
              }
              return decision;
            })
        .toList();
  }

  private static boolean decision(String command, List<String> lines) {
    for (String line : lines) {
      if (line.equals("ALLOWED")) {
        return true;
      }
      if (line.equals("DENIED") || line.contains("service.PERM_DENIED")) {
        return false;
      }
      if (line.startsWith("ERROR")) {
        throw new IllegalStateException(command + ": " + line);
      }
    }
    throw new IllegalStateException(command + ": no decision in " + lines);
  }

  @Test
  void accountRightsSummaryMatchesMailbox() {
    for (String actor : List.of(GLOBAL, USERS_ADMIN, PLAIN)) {
      given()
          .queryParam("actorId", ids.get(actor))
          .when()
          .get("/internal/rights/accounts/" + ids.get(TARGET))
          .then()
          .statusCode(200)
          .body("isGlobalAdmin", equalTo(GLOBAL.equals(actor)))
          .body("isDelegatedAdmin", equalTo(USERS_ADMIN.equals(actor)))
          .body("canWrite", equalTo(mailboxAllows("account", TARGET, actor, "modifyAccount")))
          .body(
              "canConfigure",
              equalTo(
                  mailboxAllows(
                      "account", TARGET, actor, "setDomainAdminAccountAndCalendarResourceAttrs")));
    }
  }

  @Test
  void userSessionTokenOfAGlobalAdminHoldsNoAdminRight() throws Exception {
    String token = soapAuthenticate(GLOBAL, "test-password");
    given()
        .contentType(ContentType.JSON)
        .body(
            Map.of(
                "subject", Map.of("token", token),
                "right", "modifyAccount",
                "target", Map.of("type", "account", "name", TARGET)))
        .when()
        .post("/internal/rights/check")
        .then()
        .statusCode(200)
        .body("allowed", equalTo(false));
  }

  @Test
  void invalidTokenIs401AndBadRequestsAre400() {
    post(Map.of(
            "subject",
            Map.of("token", "0_bad_00"),
            "right",
            "getAccount",
            "target",
            account(TARGET)))
        .then()
        .statusCode(401);
    post(Map.of(
            "subject",
            Map.of("accountId", ids.get(GLOBAL)),
            "right",
            "noSuchRight",
            "target",
            account(TARGET)))
        .then()
        .statusCode(400);
    post(Map.of(
            "subject",
            Map.of("accountId", ids.get(GLOBAL)),
            "right",
            "getDomain",
            "target",
            account(TARGET)))
        .then()
        .statusCode(400);
    post(Map.of(
            "subject",
            Map.of("accountId", ids.get(GLOBAL)),
            "right",
            "sendAs",
            "target",
            account(TARGET)))
        .then()
        .statusCode(400);
  }

  @Test
  void unknownSubjectOrTargetIsNotAllowed() {
    post(Map.of(
            "subject",
            Map.of("accountId", "00000000-0000-0000-0000-000000000000"),
            "right",
            "getAccount",
            "target",
            account(TARGET)))
        .then()
        .statusCode(200)
        .body("allowed", equalTo(false));
    post(Map.of(
            "subject",
            Map.of("accountId", ids.get(GLOBAL)),
            "right",
            "getAccount",
            "target",
            account("nobody@" + DOMAIN)))
        .then()
        .statusCode(200)
        .body("allowed", equalTo(false));
  }

  @Test
  void listsTheCheckableRightsOfATargetType() {
    given()
        .queryParam("targetType", "account")
        .when()
        .get("/internal/rights")
        .then()
        .statusCode(200)
        .body("find { it.name == 'modifyAccount' }.type", equalTo("setAttrs"));
    given()
        .queryParam("targetType", "server")
        .when()
        .get("/internal/rights")
        .then()
        .statusCode(400);
  }

  private static Map<String, String> account(String name) {
    return Map.of("type", "account", "name", name);
  }

  private static io.restassured.response.Response post(Map<String, Object> body) {
    return given().contentType(ContentType.JSON).body(body).when().post("/internal/rights/check");
  }

  private static boolean umAllows(
      String actor, String right, String targetType, String targetName) {
    return post(Map.of(
            "subject", Map.of("accountId", ids.get(actor)),
            "right", right,
            "target", Map.of("type", targetType, "name", targetName)))
        .then()
        .statusCode(200)
        .extract()
        .path("allowed");
  }

  private static boolean mailboxAllows(
      String targetType, String target, String actor, String right) {
    return mailboxDecisions(List.of(new Case(actor, right, targetType, target))).getFirst();
  }

  private static String id(String account) {
    String output = MailboxStackTestResource.zmprov(false, "ga " + account + " zimbraId");
    return output
        .lines()
        .filter(line -> line.startsWith("zimbraId: "))
        .map(line -> line.substring("zimbraId: ".length()).trim())
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No zimbraId for " + account + ": " + output));
  }
}
