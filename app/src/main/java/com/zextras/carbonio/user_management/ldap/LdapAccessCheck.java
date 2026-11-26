package com.zextras.carbonio.user_management.ldap;

import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPInterface;
import com.unboundid.ldap.sdk.ResultCode;
import com.zextras.carbonio.quarkus.extensions.bootstrap.ApplicationConfigService;
import com.zextras.carbonio.user_management.UserManagementServiceConfig.ApplicationConfig;
import com.zextras.carbonio.user_management.UserManagementServiceConfig.ZimbraAttributes;
import com.zextras.carbonio.user_management.UserManagementServiceConfig.ZimbraObjectClasses;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class LdapAccessCheck {

  static final String DEFAULT_COS_DN =
      "cn=" + ZimbraObjectClasses.DEFAULT_COS_NAME + ",cn=cos,cn=zimbra";

  private static final Logger logger = LoggerFactory.getLogger(LdapAccessCheck.class);

  private final LDAPInterface ldap;
  private final String bindDn;

  @Inject
  public LdapAccessCheck(LDAPConnectionPool pool, ApplicationConfigService configService) {
    this(pool, configService.get(ApplicationConfig.LDAP_USERNAME).orElse(""));
  }

  public LdapAccessCheck(LDAPInterface ldap, String bindDn) {
    this.ldap = ldap;
    this.bindDn = bindDn;
  }

  void onStart(@Observes @Priority(Interceptor.Priority.APPLICATION) StartupEvent event) {
    try {
      verify();
    } catch (IllegalStateException e) {
      logger.error(e.getMessage());
      throw e;
    }
  }

  public void verify() {
    requireReadable(LdapDirectory.CONFIG_DN, ZimbraAttributes.AUTH_TOKEN_KEY);
    requireReadable(DEFAULT_COS_DN, ZimbraAttributes.ID);
  }

  private void requireReadable(String dn, String attribute) {
    Entry entry;
    try {
      entry = ldap.getEntry(dn, attribute);
    } catch (LDAPException e) {
      if (e.getResultCode() == ResultCode.INVALID_CREDENTIALS) {
        throw new IllegalStateException(
            "LDAP rejected the password of "
                + bindDn
                + ": run /usr/libexec/carbonio-user-management/ldap-credentials as root",
            e);
      }
      throw new IllegalStateException(
          "Cannot read " + dn + " from LDAP as " + bindDn + ": " + e.getMessage(), e);
    }
    if (entry == null || !entry.hasAttribute(attribute)) {
      throw new IllegalStateException(
          bindDn
              + " cannot read "
              + attribute
              + " of "
              + dn
              + ": the LDAP server lacks the carbonio-user-management access rules of"
              + " carbonio-directory-server");
    }
  }
}
