package com.zextras.carbonio.user_management.ldap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.ldif.LDIFReader;
import com.zextras.carbonio.user_management.record.AccountDirectory;
import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

class AccountDirectoryServiceIT {

  private static final String LDAP_IMAGE = "registry.dev.zextras.com/dev/carbonio-openldap:devel";
  private static final int LDAP_PORT = 1389;
  private static final String BIND_DN = "uid=zimbra,cn=admins,cn=zimbra";
  private static final String BIND_PASSWORD = "password";
  private static final String BASE_DN = "";

  private static final String DOMAIN_ID = "094660b7-b01c-4d75-892b-733d48da7c2c";
  private static final String DOMAIN_DEFAULT_COS_ID = "e00428a1-0c00-11d9-836a-000d93afea2a";
  private static final String ACCOUNT_WITH_COS_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
  private static final String ACCOUNT_COS_ID = "cos-1111-2222-3333-444444444444";
  private static final String ACCOUNT_WITHOUT_COS_ID = "b2c3d4e5-f6a7-8901-bcde-f12345678901";
  private static final String DOMAIN_WITHOUT_COS_ID = "11111111-2222-3333-4444-555555555555";
  private static final String ACCOUNT_IN_DOMAIN_WITHOUT_COS_ID = "c3d4e5f6-a7b8-9012-cdef-123456789012";

  private static GenericContainer<?> ldapContainer;
  private static LDAPConnectionPool pool;
  private static AccountDirectoryService service;
  private static String defaultCosId;

  @BeforeAll
  static void setUp() throws Exception {
    ldapContainer = new GenericContainer<>(LDAP_IMAGE)
        .withExposedPorts(LDAP_PORT)
        .waitingFor(Wait.forLogMessage(".*slapd starting.*", 2)
            .withStartupTimeout(Duration.ofMinutes(5)));
    ldapContainer.start();

    pool = new LDAPConnectionPool(new LDAPConnection(
        ldapContainer.getHost(), ldapContainer.getMappedPort(LDAP_PORT), BIND_DN, BIND_PASSWORD), 2);
    service = new AccountDirectoryService(pool, BASE_DN);

    loadLdif("ldap/account-directory-test-data.ldif");
    defaultCosId = lookupDefaultCosId();
  }

  @AfterAll
  static void tearDown() {
    if (pool != null) {
      pool.close();
    }
    if (ldapContainer != null) {
      ldapContainer.stop();
    }
  }

  @Test
  void returnsCosFromAccount() {
    assertThat(service.findByAccountId(ACCOUNT_WITH_COS_ID))
        .contains(new AccountDirectory(ACCOUNT_WITH_COS_ID, ACCOUNT_COS_ID, DOMAIN_ID));
  }

  @Test
  void fallsBackToDomainDefaultCos() {
    assertThat(service.findByAccountId(ACCOUNT_WITHOUT_COS_ID))
        .contains(new AccountDirectory(ACCOUNT_WITHOUT_COS_ID, DOMAIN_DEFAULT_COS_ID, DOMAIN_ID));
  }

  @Test
  void fallsBackToGlobalDefaultCos() {
    assertThat(service.findByAccountId(ACCOUNT_IN_DOMAIN_WITHOUT_COS_ID))
        .contains(new AccountDirectory(
            ACCOUNT_IN_DOMAIN_WITHOUT_COS_ID, defaultCosId, DOMAIN_WITHOUT_COS_ID));
  }

  @Test
  void returnsEmptyForUnknownAccount() {
    assertThat(service.findByAccountId("non-existent-id")).isEqualTo(Optional.empty());
  }

  @Test
  void escapesFilterValues() {
    assertThat(service.findByAccountId("*")).isEqualTo(Optional.empty());
  }

  @Test
  void wrapsLdapFailures() {
    AccountDirectoryService broken = new AccountDirectoryService(pool, "dc=does-not-exist");

    assertThatThrownBy(() -> broken.findByAccountId(ACCOUNT_WITH_COS_ID))
        .isInstanceOf(DirectoryException.class);
  }

  private static String lookupDefaultCosId() throws Exception {
    SearchResult result = pool.search(BASE_DN, SearchScope.SUB,
        Filter.createANDFilter(
            Filter.createEqualityFilter("cn", "default"),
            Filter.createEqualityFilter("objectClass", "zimbraCOS")),
        "zimbraId");
    assertThat(result.getEntryCount()).as("default COS in the LDAP image").isPositive();
    return result.getSearchEntries().getFirst().getAttributeValue("zimbraId");
  }

  private static void loadLdif(String resource) throws Exception {
    try (InputStream stream = AccountDirectoryServiceIT.class.getClassLoader()
        .getResourceAsStream(resource);
        LDIFReader reader = new LDIFReader(stream)) {
      Entry entry;
      while ((entry = reader.readEntry()) != null) {
        pool.add(entry);
      }
    }
  }
}
