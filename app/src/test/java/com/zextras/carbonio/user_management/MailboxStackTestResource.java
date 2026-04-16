// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management;

import com.zextras.carbonio.quarkus.extensions.bootstrap.CarbonioServiceConfig;
import com.zextras.carbonio.user_management.UserManagementServiceConfig;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Starts the Carbonio mailbox stack (mailbox + dependencies) and a WireMock container
 * that stubs Consul endpoints for integration tests.
 *
 * <p><b>Testing philosophy:</b>
 * <ul>
 *   <li>Mailbox (+ openldap, mariadb, postfix) — real containers: tests exercise the full
 *       SOAP authentication and user lookup flow.</li>
 *   <li>Consul — WireMock stub: the bootstrap extension reads KV config and registers the
 *       service; a real Consul instance is not needed.</li>
 * </ul>
 *
 * <p>All container fields are {@code static} so that a single stack is shared across all IT
 * classes within one JVM run. {@link #stop()} is a no-op; Testcontainers' JVM shutdown hook
 * handles cleanup on exit.
 */
public class MailboxStackTestResource implements QuarkusTestResourceLifecycleManager {

  private static final Logger log = LoggerFactory.getLogger(MailboxStackTestResource.class);

  /** Accessible from test classes to build SOAP auth requests directly against mailbox. */
  public static volatile String mailboxBaseUrl;

  /** zimbraId of the test user, resolved via zmprov to avoid warming the application cache. */
  public static volatile String testUserId;

  private static volatile boolean started = false;
  private static Map<String, String> cachedConfig;

  private static Network network;
  private static GenericContainer<?> openldap;
  private static GenericContainer<?> mariadb;
  private static GenericContainer<?> postfix;
  private static GenericContainer<?> mailbox;
  private static GenericContainer<?> wireMock;

  @Override
  public Map<String, String> start() {
    if (started) {
      log.info("Carbonio stack already running — reusing singleton containers.");
      return cachedConfig;
    }

    log.info("Starting Carbonio stack with individual Testcontainers...");

    network = Network.newNetwork();

    // --- Mailbox stack (shared network) ---

    openldap = new GenericContainer<>("registry.dev.zextras.com/dev/carbonio-openldap:latest")
        .withNetwork(network)
        .withNetworkAliases("carbonio-openldap")
        .withExposedPorts(1389)
        .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(5)));

    mariadb = new GenericContainer<>("registry.dev.zextras.com/dev/carbonio-mariadb:latest")
        .withNetwork(network)
        .withNetworkAliases("carbonio-mariadb")
        .withEnv("MARIADB_ROOT_PASSWORD", "password")
        .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)));

    postfix = new GenericContainer<>("registry.dev.zextras.com/dev/carbonio-mta:latest")
        .withNetwork(network)
        .withNetworkAliases("carbonio-postfix")
        .withEnv("LDAP_HOST", "carbonio-openldap")
        .withEnv("LDAP_PORT", "1389")
        .withEnv("LDAP_ROOT_PASSWORD", "qh6hWZvc")
        .withEnv("LDAP_ADMIN_PASSWORD", "password")
        .withExposedPorts(25)
        .dependsOn(openldap)
        .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(5)));

    mailbox = new GenericContainer<>("registry.dev.zextras.com/dev/carbonio-mailbox:latest")
        .withNetwork(network)
        .withNetworkAliases("carbonio-mailbox")
        .withCreateContainerCmdModifier(cmd -> cmd.withHostName("docker.carbonio.localhost"))
        .withEnv("LDAP_URL", "ldap://carbonio-openldap:1389")
        .withEnv("LDAP_ROOT_PASSWORD", "qh6hWZvc")
        .withEnv("LDAP_ADMIN_PASSWORD", "password")
        .withEnv("MARIADB_ROOT_PASSWORD", "password")
        .withEnv("MARIADB_URL", "carbonio-mariadb")
        .withEnv("MARIADB_PORT", "3306")
        .withExposedPorts(8080)
        .dependsOn(openldap, postfix, mariadb)
        .waitingFor(Wait.forHttp("/service/health/ready").forPort(8080)
            .withStartupTimeout(Duration.ofMinutes(10)));

    // --- Consul mock (WireMock, no shared network needed) ---

    wireMock = new GenericContainer<>("wiremock/wiremock:3.9.2")
        .withExposedPorts(8080)
        .waitingFor(Wait.forHttp("/__admin/health").forPort(8080)
            .withStartupTimeout(Duration.ofMinutes(2)));

    // Start all containers in parallel, respecting dependsOn graph
    log.info("Starting all containers in parallel...");
    Startables.deepStart(mailbox, wireMock).join();
    log.info("All containers started");

    // Configure WireMock stubs for Consul
    String wireMockAdminUrl = "http://" + wireMock.getHost() + ":" + wireMock.getMappedPort(8080);
    try {
      setupConsulStubs(wireMockAdminUrl);
    } catch (Exception e) {
      throw new RuntimeException("Failed to configure Consul WireMock stubs", e);
    }

    // Provision test accounts (only on first start)
    provisionTestAccounts();

    int mailboxPort = mailbox.getMappedPort(8080);
    mailboxBaseUrl = "http://localhost:" + mailboxPort;
    log.info("Mailbox available at {}", mailboxBaseUrl);

    cachedConfig = Map.ofEntries(
        Map.entry(CarbonioServiceConfig.NETWORKING_CONFIG_PREFIX
            + CarbonioServiceConfig.NetworkingConfig.SERVICE_HOST, "localhost"),
        Map.entry(CarbonioServiceConfig.NETWORKING_CONFIG_PREFIX
            + CarbonioServiceConfig.NetworkingConfig.SERVICE_DISCOVER_HOST, wireMock.getHost()),
        Map.entry(CarbonioServiceConfig.NETWORKING_CONFIG_PREFIX
            + CarbonioServiceConfig.NetworkingConfig.SERVICE_DISCOVER_PORT,
            String.valueOf(wireMock.getMappedPort(8080))),
        Map.entry(CarbonioServiceConfig.NETWORKING_CONFIG_PREFIX
            + UserManagementServiceConfig.NetworkingConfig.MAILBOX_HOST, "localhost"),
        Map.entry(CarbonioServiceConfig.NETWORKING_CONFIG_PREFIX
            + UserManagementServiceConfig.NetworkingConfig.MAILBOX_PORT,
            String.valueOf(mailboxPort))
    );
    started = true;
    return cachedConfig;
  }

  @Override
  public void stop() {
    // Containers are static singletons: they persist for the full test-run JVM lifetime.
    // Testcontainers' JVM shutdown hook will stop them when the JVM exits.
  }

  /**
   * Consul WireMock stubs: KV recursive fetch, service registration/deregistration,
   * and service discovery endpoints.
   *
   * <p>CarbonioBootstrapFactory issues a SINGLE recursive GET:
   *   GET /v1/kv/carbonio-user-management/?recurse
   * and expects a JSON array of all KV entries under that prefix.
   */
  private static void setupConsulStubs(String wireMockAdminUrl) throws Exception {
    HttpClient client = HttpClient.newHttpClient();

    // Application config — recursive KV stub for the whole prefix
    postConsulKvRecursiveStub(client, wireMockAdminUrl, "carbonio-user-management/",
        new String[][]{
            {"carbonio-user-management/cache/userinfo-ttl", "43200"},
        });

    // Catch-all unknown KV prefixes → 404
    postStub(client, wireMockAdminUrl,
        "{\"priority\":10,"
        + "\"request\":{\"method\":\"GET\",\"urlPathPattern\":\"/v1/kv/.*\"},"
        + "\"response\":{\"status\":404}}");

    // Service registration / deregistration → 200
    for (String pattern : new String[]{
        "/v1/agent/service/register.*", "/v1/agent/service/deregister/.*",
        "/v1/agent/check/register.*",   "/v1/agent/check/deregister/.*"}) {
      postStub(client, wireMockAdminUrl,
          "{\"request\":{\"method\":\"PUT\",\"urlPathPattern\":\"" + pattern + "\"},"
          + "\"response\":{\"status\":200}}");
    }

    // Service discovery → empty array
    for (String pattern : new String[]{"/v1/health/service/.*", "/v1/catalog/service/.*"}) {
      postStub(client, wireMockAdminUrl,
          "{\"request\":{\"method\":\"GET\",\"urlPathPattern\":\"" + pattern + "\"},"
          + "\"response\":{\"status\":200,"
          + "\"headers\":{\"Content-Type\":\"application/json\"},\"body\":\"[]\"}}");
    }
  }

  /**
   * Registers a single WireMock stub that matches the Consul recursive KV fetch:
   *   GET /v1/kv/{prefix}?recurse   (urlPath ignores the query string)
   *
   * @param prefix    the KV prefix including trailing slash
   * @param kvEntries array of {key, plainTextValue} pairs
   */
  private static void postConsulKvRecursiveStub(
      HttpClient client, String baseUrl, String prefix, String[][] kvEntries) throws Exception {
    StringBuilder arrayBody = new StringBuilder("[");
    for (int i = 0; i < kvEntries.length; i++) {
      String key   = kvEntries[i][0];
      String value = kvEntries[i][1];
      String b64   = Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
      if (i > 0) arrayBody.append(',');
      arrayBody.append("{\"LockIndex\":0,\"Key\":\"").append(key)
               .append("\",\"Flags\":0,\"Value\":\"").append(b64)
               .append("\",\"CreateIndex\":1,\"ModifyIndex\":1}");
    }
    arrayBody.append("]");

    String escapedBody = arrayBody.toString()
        .replace("\\", "\\\\")
        .replace("\"", "\\\"");

    postStub(client, baseUrl,
        "{\"priority\":1,"
        + "\"request\":{\"method\":\"GET\",\"urlPath\":\"/v1/kv/" + prefix + "\"},"
        + "\"response\":{\"status\":200,"
        + "\"headers\":{\"Content-Type\":\"application/json\"},"
        + "\"body\":\"" + escapedBody + "\"}}");
  }

  private static void postStub(HttpClient client, String baseUrl, String stubJson)
      throws Exception {
    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl + "/__admin/mappings"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(stubJson))
        .build();
    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() != 201) {
      throw new RuntimeException(
          "Failed to register stub (HTTP " + resp.statusCode() + "): " + resp.body());
    }
  }

  /**
   * Creates test accounts in mailbox via zmprov (execInContainer) and resolves the zimbraId
   * directly from LDAP — without ever calling the application — so that integration tests
   * start with a cold cache.
   */
  private void provisionTestAccounts() {
    log.info("Provisioning test accounts in mailbox...");
    try {
      // Single execInContainer: waits for zmprov readiness, then provisions in one shot
      var result = mailbox.execInContainer("sh", "-c",
          "for i in $(seq 1 30); do "
          + "  echo 'gd carbonio.localhost' | zmprov 2>&1 | grep -qv ERROR && break; "
          + "  sleep 2; "
          + "done && "
          + "zmprov <<'EOF'\n"
          + "cd carbonio.localhost\n"
          + "mcf zimbraSmtpHostname carbonio-postfix\n"
          + "mcf zimbraDefaultDomainName carbonio.localhost\n"
          + "ca test-user@carbonio.localhost test-password\n"
          + "EOF");

      if (result.getExitCode() == 0) {
        log.info("Test account provisioning succeeded");
      } else {
        log.warn("Provisioning exited with code {} (accounts may already exist): {}",
            result.getExitCode(), result.getStderr());
      }

      // Resolve zimbraId via zmprov ga (reads from LDAP, never touches the application cache)
      var gaResult = mailbox.execInContainer("sh", "-c",
          "zmprov ga test-user@carbonio.localhost zimbraId | grep zimbraId: | awk '{print $2}'");
      if (gaResult.getExitCode() == 0 && !gaResult.getStdout().isBlank()) {
        testUserId = gaResult.getStdout().trim();
        log.info("Resolved test user zimbraId: {}", testUserId);
      } else {
        throw new IllegalStateException(
            "Failed to resolve zimbraId: exit=" + gaResult.getExitCode()
                + " stdout=" + gaResult.getStdout()
                + " stderr=" + gaResult.getStderr());
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Test account provisioning failed", e);
    }
  }
}
