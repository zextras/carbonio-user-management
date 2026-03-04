// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management;

import com.zextras.carbonio.quarkus.extensions.bootstrap.CarbonioServiceConfig;
import com.zextras.carbonio.quarkus.extensions.bootstrap.ConsulTestHelper;
import com.zextras.carbonio.quarkus.extensions.bootstrap.db.CarbonioDatabaseServiceConfig;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Starts the full Carbonio stack (mailbox + dependencies, consul, postgres) using individual
 * Testcontainers for integration tests that need a real mailbox instance.
 *
 * <p>Each service runs as an individual container with random host ports. Dependencies are modeled
 * via {@code dependsOn()} and started in parallel using {@link Startables#deepStart}.
 * Consul KV is initialized via {@link ConsulTestHelper}. Test account provisioning is done
 * via {@code execInContainer} on the mailbox container.
 */
public class MailboxStackTestResource implements QuarkusTestResourceLifecycleManager {

  private static final Logger log = LoggerFactory.getLogger(MailboxStackTestResource.class);

  /** Accessible from test classes to build SOAP auth requests directly against mailbox. */
  static volatile String mailboxBaseUrl;

  private Network network;
  private GenericContainer<?> openldap;
  private GenericContainer<?> mariadb;
  private GenericContainer<?> postfix;
  private GenericContainer<?> mailbox;
  private ConsulContainer consul;
  private PostgreSQLContainer<?> postgres;

  @Override
  public Map<String, String> start() {
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

    // --- Independent services (no shared network needed) ---

    consul = new ConsulContainer("hashicorp/consul:1.22.3");

    postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("carbonio_user_management")
        .withUsername("carbonio")
        .withPassword("carbonio");

    // Start all containers in parallel, respecting dependsOn graph
    log.info("Starting all containers in parallel...");
    Startables.deepStart(mailbox, consul, postgres).join();
    log.info("All containers started");

    // Init Consul KV
    initConsulKv();

    // Provision test accounts
    provisionTestAccounts();

    int mailboxPort = mailbox.getMappedPort(8080);
    mailboxBaseUrl = "http://localhost:" + mailboxPort;
    log.info("Mailbox available at {}", mailboxBaseUrl);

    String consulHost = consul.getHost();
    int consulPort = consul.getFirstMappedPort();
    String postgresHost = postgres.getHost();
    int postgresPort = postgres.getFirstMappedPort();
    String jdbcUrl = "jdbc:postgresql://" + postgresHost + ":" + postgresPort
        + "/carbonio_user_management";

    return Map.ofEntries(
        Map.entry(CarbonioServiceConfig.NETWORKING_CONFIG_PREFIX
            + CarbonioServiceConfig.NetworkingConfig.SERVICE_HOST, "localhost"),
        Map.entry(CarbonioServiceConfig.NETWORKING_CONFIG_PREFIX
            + CarbonioServiceConfig.NetworkingConfig.SERVICE_DISCOVER_HOST, consulHost),
        Map.entry(CarbonioServiceConfig.NETWORKING_CONFIG_PREFIX
            + CarbonioServiceConfig.NetworkingConfig.SERVICE_DISCOVER_PORT,
            String.valueOf(consulPort)),
        Map.entry(CarbonioServiceConfig.NETWORKING_CONFIG_PREFIX
            + CarbonioDatabaseServiceConfig.NetworkingConfig.POSTGRESQL_HOST, postgresHost),
        Map.entry(CarbonioServiceConfig.NETWORKING_CONFIG_PREFIX
            + CarbonioDatabaseServiceConfig.NetworkingConfig.POSTGRESQL_PORT,
            String.valueOf(postgresPort)),
        Map.entry(CarbonioServiceConfig.NETWORKING_CONFIG_PREFIX
            + UserManagementServiceConfig.NetworkingConfig.MAILBOX_HOST, "localhost"),
        Map.entry(CarbonioServiceConfig.NETWORKING_CONFIG_PREFIX
            + UserManagementServiceConfig.NetworkingConfig.MAILBOX_PORT,
            String.valueOf(mailboxPort)),
        Map.entry("quarkus.datasource.jdbc.url", jdbcUrl),
        Map.entry("quarkus.datasource.username", "carbonio"),
        Map.entry("quarkus.datasource.password", "carbonio")
    );
  }

  @Override
  public void stop() {
    log.info("Stopping Carbonio stack...");
    // Testcontainers stops containers in reverse order; explicit stop for clarity
    stopQuietly(mailbox);
    stopQuietly(postfix);
    stopQuietly(mariadb);
    stopQuietly(openldap);
    stopQuietly(consul);
    stopQuietly(postgres);
    if (network != null) {
      network.close();
    }
  }

  private void stopQuietly(GenericContainer<?> container) {
    if (container != null) {
      try {
        container.stop();
      } catch (Exception e) {
        log.warn("Error stopping container: {}", e.getMessage());
      }
    }
  }

  private void initConsulKv() {
    String consulHost = consul.getHost();
    int consulPort = consul.getFirstMappedPort();
    log.info("Initializing Consul KV at {}:{}", consulHost, consulPort);

    ConsulTestHelper helper = new ConsulTestHelper(consulHost, consulPort);
    String svc = "carbonio-user-management";
    helper.putValue(svc + "/" + CarbonioDatabaseServiceConfig.ApplicationConfig.DB_NAME,
        "carbonio_user_management");
    helper.putValue(svc + "/" + CarbonioDatabaseServiceConfig.ApplicationConfig.DB_USERNAME,
        "carbonio");
    helper.putValue(svc + "/" + CarbonioDatabaseServiceConfig.ApplicationConfig.DB_PASSWORD,
        "carbonio");
    helper.putValue(svc + "/" + UserManagementServiceConfig.ApplicationConfig.CACHE_USERINFO_TTL,
        "3600");
    helper.putValue(svc + "/" + UserManagementServiceConfig.ApplicationConfig.CACHE_USERMYSELF_TTL,
        "3600");

    log.info("Consul KV initialization complete");
  }

  /**
   * Creates test accounts in mailbox via zmprov (execInContainer).
   * The mailbox container is already healthy at this point.
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
          + "ca test-admin@carbonio.localhost test-password zimbraIsAdminAccount TRUE\n"
          + "EOF");

      if (result.getExitCode() == 0) {
        log.info("Test account provisioning succeeded");
      } else {
        log.warn("Provisioning exited with code {} (accounts may already exist): {}",
            result.getExitCode(), result.getStderr());
      }
    } catch (Exception e) {
      throw new RuntimeException("Test account provisioning failed", e);
    }
  }
}
