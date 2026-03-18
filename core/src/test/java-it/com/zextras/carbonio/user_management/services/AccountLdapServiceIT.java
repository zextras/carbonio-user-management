package com.zextras.carbonio.user_management.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldif.LDIFReader;
import com.zextras.carbonio.user_management.generated.model.AccountInfo;
import com.zextras.mailbox.util.InMemoryLdapServer;
import java.io.InputStream;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class AccountLdapServiceIT {

  private static final String LDAP_IMAGE = "registry.dev.zextras.com/dev/carbonio-openldap";
  private static final int LDAP_PORT = 1389;
  private static final String BIND_DN = "uid=zimbra,cn=admins,cn=zimbra";
  private static final String BIND_PASSWORD = "password";
  private static final String BASE_DN = "";

  // see ldif
  private static final String DOMAIN_ID = "094660b7-b01c-4d75-892b-733d48da7c2c";
  private static final String DOMAIN_DEFAULT_COS_ID = "e00428a1-0c00-11d9-836a-000d93afea2a";

  private static final String TEST_ACCOUNT_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
  private static final String COS_ID = "cos-1111-2222-3333-444444444444";

  private static final String NO_COS_ACCOUNT_ID = "b2c3d4e5-f6a7-8901-bcde-f12345678901";

  private static LDAPConnectionPool connectionPool;
  private static AccountLdapService accountLdapService;

  @Container
  private static final GenericContainer<?> ldapContainer =
      new GenericContainer<>(LDAP_IMAGE)
          .withExposedPorts(LDAP_PORT)
          .waitingFor(Wait.forLogMessage(".*slapd starting.*", 2));


  @BeforeAll
  static void setUp() throws Exception {

    LDAPConnection connection = new LDAPConnection(
        ldapContainer.getHost(),
        ldapContainer.getMappedPort(LDAP_PORT),
        BIND_DN,
        BIND_PASSWORD
    );
    connectionPool = new LDAPConnectionPool(connection, 2);
    accountLdapService = new AccountLdapService(connectionPool, BASE_DN);

    loadLdifData();
  }

  @AfterAll
  static void tearDown() {
    if (connectionPool != null) {
      connectionPool.close();
    }
  }

  @Test
  void getCosAndDomainByAccountId_existingAccount_returnsAccountInfo() {
    Optional<AccountInfo> result = accountLdapService.getCosAndDomainByAccountId(TEST_ACCOUNT_ID);

    assertThat(result).isPresent();
    assertThat(result.get().getCosId()).isEqualTo(COS_ID);
    assertThat(result.get().getDomainId()).isEqualTo(DOMAIN_ID);
  }

  @Test
  void getCosAndDomainByAccountId_accountWithoutCos_fallsToDomainDefault() {
    Optional<AccountInfo> result = accountLdapService.getCosAndDomainByAccountId(NO_COS_ACCOUNT_ID);

    assertThat(result).isPresent();
    assertThat(result.get().getCosId()).isEqualTo(DOMAIN_DEFAULT_COS_ID);
    assertThat(result.get().getDomainId()).isEqualTo(DOMAIN_ID);
  }

  @Test
  void getCosAndDomainByAccountId_nonExistentAccount_returnsEmpty() {
    Optional<AccountInfo> result = accountLdapService.getCosAndDomainByAccountId("non-existent-id");

    assertThat(result).isEmpty();
  }

  private static void loadLdifData() throws Exception {
    try (InputStream ldifStream = AccountLdapServiceIT.class.getClassLoader()
        .getResourceAsStream("ldap/account-ldap-service-test-data.ldif");
        LDIFReader ldifReader = new LDIFReader(ldifStream)) {
      LDAPConnection conn = connectionPool.getConnection();
      try {
        com.unboundid.ldap.sdk.Entry entry;
        while ((entry = ldifReader.readEntry()) != null) {
          conn.add(entry);
        }
      } finally {
        connectionPool.releaseConnection(conn);
      }
    }
  }
}
