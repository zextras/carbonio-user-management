package com.zextras.carbonio.user_management.ldap;

import static com.zextras.carbonio.user_management.UserManagementServiceConfig.DEFAULT_LDAP_BIND_DN;
import static com.zextras.carbonio.user_management.UserManagementServiceConfig.LDAP_POOL_SIZE;

import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.SimpleBindRequest;
import com.zextras.carbonio.quarkus.extensions.bootstrap.ApplicationConfigService;
import com.zextras.carbonio.quarkus.extensions.bootstrap.NetworkingConfigService;
import com.zextras.carbonio.user_management.UserManagementServiceConfig.ApplicationConfig;
import com.zextras.carbonio.user_management.UserManagementServiceConfig.NetworkingConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class LdapConnectionPoolProducer {

  private static final Logger logger = LoggerFactory.getLogger(LdapConnectionPoolProducer.class);

  private final NetworkingConfigService networkingConfigService;
  private final ApplicationConfigService applicationConfigService;

  @Inject
  public LdapConnectionPoolProducer(
      NetworkingConfigService networkingConfigService,
      ApplicationConfigService applicationConfigService) {
    this.networkingConfigService = networkingConfigService;
    this.applicationConfigService = applicationConfigService;
  }

  @Produces
  @Singleton
  public LDAPConnectionPool ldapConnectionPool() throws LDAPException {
    String urls =
        required(
            networkingConfigService.get(NetworkingConfig.LDAP_URL).orElse(null),
            NetworkingConfig.LDAP_URL);
    String bindDn =
        applicationConfigService
            .get(ApplicationConfig.LDAP_BIND_DN)
            .filter(value -> !value.isBlank())
            .orElse(DEFAULT_LDAP_BIND_DN);
    String bindPassword =
        required(
            applicationConfigService.get(ApplicationConfig.LDAP_BIND_PASSWORD).orElse(null),
            ApplicationConfig.LDAP_BIND_PASSWORD);

    logger.info("Connecting to LDAP at {} as {}", urls, bindDn);
    return new LDAPConnectionPool(
        LdapServers.from(urls), new SimpleBindRequest(bindDn, bindPassword), LDAP_POOL_SIZE);
  }

  public void close(@Disposes LDAPConnectionPool pool) {
    pool.close();
  }

  private static String required(String value, String key) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Missing required config: " + key);
    }
    return value;
  }
}
