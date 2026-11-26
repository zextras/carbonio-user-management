package com.zextras.carbonio.user_management.ldap;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.SimpleBindRequest;
import com.unboundid.ldap.sdk.SingleServerSet;
import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LdapAccessCheckTest {

  private static final String USER_MANAGEMENT_DN =
      "uid=carbonio-user-management,cn=appaccts,cn=zimbra";
  private static final String PASSWORD = "user-management-password";

  private InMemoryDirectoryServer server;

  @BeforeEach
  void setUp() throws Exception {
    InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig("cn=zimbra");
    config.setSchema(null);
    config.addAdditionalBindCredentials(USER_MANAGEMENT_DN, PASSWORD);
    server = new InMemoryDirectoryServer(config);
    server.startListening();
    server.add("dn: cn=zimbra", "objectClass: organizationalRole", "cn: zimbra");
    server.add("dn: cn=cos,cn=zimbra", "objectClass: organizationalRole", "cn: cos");
  }

  @AfterEach
  void tearDown() {
    server.shutDown(true);
  }

  @Test
  void passesWhenTheAuthTokenKeyAndTheDefaultCosAreReadable() throws Exception {
    config("zimbraAuthTokenKey: 0:0123456789abcdef");
    defaultCos("zimbraId: e00428a1-0c00-11d9-836a-000d93afea2a");

    assertThatCode(() -> check(PASSWORD).verify()).doesNotThrowAnyException();
  }

  @Test
  void hiddenAuthTokenKeyNamesTheMissingDirectoryServerRules() throws Exception {
    config();
    defaultCos("zimbraId: e00428a1-0c00-11d9-836a-000d93afea2a");

    assertThatThrownBy(() -> check(PASSWORD).verify())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            USER_MANAGEMENT_DN
                + " cannot read zimbraAuthTokenKey of cn=config,cn=zimbra: the LDAP server lacks"
                + " the carbonio-user-management access rules of carbonio-directory-server");
  }

  @Test
  void hiddenCosIdFailsAsWhenLdapAnonDropsTheUserManagementClause() throws Exception {
    config("zimbraAuthTokenKey: 0:0123456789abcdef");
    defaultCos();

    assertThatThrownBy(() -> check(PASSWORD).verify())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith(
            USER_MANAGEMENT_DN + " cannot read zimbraId of cn=default,cn=cos,cn=zimbra");
  }

  @Test
  void invisibleConfigEntryFails() {
    assertThatThrownBy(() -> check(PASSWORD).verify())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith(
            USER_MANAGEMENT_DN + " cannot read zimbraAuthTokenKey of cn=config,cn=zimbra");
  }

  @Test
  void rejectedPasswordPointsToTheCredentialsScript() throws Exception {
    config("zimbraAuthTokenKey: 0:0123456789abcdef");
    defaultCos("zimbraId: e00428a1-0c00-11d9-836a-000d93afea2a");

    assertThatThrownBy(() -> check("stale-password").verify())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "LDAP rejected the password of "
                + USER_MANAGEMENT_DN
                + ": run /usr/libexec/carbonio-user-management/ldap-credentials as root");
  }

  @Test
  void unreachableLdapFailsWithTheReadThatFailed() throws Exception {
    LDAPConnectionPool pool =
        new LDAPConnectionPool(
            new SingleServerSet("127.0.0.1", freePort()),
            new SimpleBindRequest(USER_MANAGEMENT_DN, PASSWORD),
            0,
            1,
            1,
            null,
            false);

    assertThatThrownBy(() -> new LdapAccessCheck(pool, USER_MANAGEMENT_DN).verify())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith(
            "Cannot read cn=config,cn=zimbra from LDAP as " + USER_MANAGEMENT_DN + ": ");
  }

  private LdapAccessCheck check(String password) throws Exception {
    LDAPConnectionPool pool =
        new LDAPConnectionPool(
            new SingleServerSet("127.0.0.1", server.getListenPort()),
            new SimpleBindRequest(USER_MANAGEMENT_DN, password),
            0,
            1,
            1,
            null,
            false);
    return new LdapAccessCheck(pool, USER_MANAGEMENT_DN);
  }

  private void config(String... attributes) throws Exception {
    add("dn: cn=config,cn=zimbra", "objectClass: zimbraGlobalConfig", "cn: config", attributes);
  }

  private void defaultCos(String... attributes) throws Exception {
    add("dn: cn=default,cn=cos,cn=zimbra", "objectClass: zimbraCOS", "cn: default", attributes);
  }

  private void add(String dn, String objectClass, String cn, String... attributes)
      throws Exception {
    String[] lines = new String[3 + attributes.length];
    lines[0] = dn;
    lines[1] = objectClass;
    lines[2] = cn;
    System.arraycopy(attributes, 0, lines, 3, attributes.length);
    server.add(lines);
  }

  private static int freePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }
}
