package com.zextras.carbonio.user_management.ldap;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.zextras.carbonio.quarkus.extensions.bootstrap.ApplicationConfigService;
import com.zextras.carbonio.quarkus.extensions.bootstrap.NetworkingConfigService;
import com.zextras.carbonio.user_management.UserManagementServiceConfig.ApplicationConfig;
import com.zextras.carbonio.user_management.UserManagementServiceConfig.NetworkingConfig;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LdapConnectionPoolProducerTest {

  @Test
  void unreachableLdapDoesNotFailPoolCreationAndReadsThrowDirectoryException() throws Exception {
    int closedPort = closedPort();
    NetworkingConfigService networking = mock(NetworkingConfigService.class);
    when(networking.get(NetworkingConfig.LDAP_URL))
        .thenReturn(Optional.of("ldap://127.0.0.1:" + closedPort));
    when(networking.get(NetworkingConfig.LDAP_STARTTLS)).thenReturn(Optional.empty());
    ApplicationConfigService application = mock(ApplicationConfigService.class);
    when(application.get(ApplicationConfig.LDAP_BIND_DN)).thenReturn(Optional.empty());
    when(application.get(ApplicationConfig.LDAP_BIND_PASSWORD)).thenReturn(Optional.of("secret"));

    LdapConnectionPoolProducer producer = new LdapConnectionPoolProducer(networking, application);
    LDAPConnectionPool pool = producer.ldapConnectionPool();
    try {
      LdapDirectory directory = new LdapDirectory(pool, "dc=root");

      assertThatThrownBy(() -> directory.accountById("any", LdapDirectory.ACCOUNT_INFO_ATTRIBUTES)).isInstanceOf(DirectoryException.class);
      assertThatThrownBy(directory::config).isInstanceOf(DirectoryException.class);
    } finally {
      producer.close(pool);
    }
  }

  @Test
  void missingBindPasswordStaysAnIllegalStateException() {
    NetworkingConfigService networking = mock(NetworkingConfigService.class);
    when(networking.get(NetworkingConfig.LDAP_URL)).thenReturn(Optional.of("ldap://127.0.0.1:1"));
    ApplicationConfigService application = mock(ApplicationConfigService.class);
    when(application.get(ApplicationConfig.LDAP_BIND_DN)).thenReturn(Optional.empty());
    when(application.get(ApplicationConfig.LDAP_BIND_PASSWORD)).thenReturn(Optional.empty());

    LdapConnectionPoolProducer producer = new LdapConnectionPoolProducer(networking, application);

    assertThatThrownBy(producer::ldapConnectionPool)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(ApplicationConfig.LDAP_BIND_PASSWORD);
  }

  private static int closedPort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }
}
