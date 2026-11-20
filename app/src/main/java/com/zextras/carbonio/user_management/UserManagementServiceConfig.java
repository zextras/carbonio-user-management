// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management;

import com.zextras.carbonio.quarkus.extensions.bootstrap.ConfigKey;

public final class UserManagementServiceConfig {

  private UserManagementServiceConfig() {}

  public static final class NetworkingConfig {
    private NetworkingConfig() {}

    @ConfigKey public static final String MAILBOX_INTERNAL_HOST = "carbonio.mailbox.internal.host";
    @ConfigKey public static final String MAILBOX_INTERNAL_PORT = "carbonio.mailbox.internal.port";

    @ConfigKey(ifNotPresent = "Directory endpoints answer 500")
    public static final String LDAP_URL = "carbonio.ldap.url";
  }

  public static final class ApplicationConfig {
    private ApplicationConfig() {}

    @ConfigKey(ifNotPresent = "Uses remaining token validity time")
    public static final String CACHE_USERMYSELF_TTL = "cache.usermyself-ttl";

    @ConfigKey public static final String CACHE_USERINFO_TTL = "cache.userinfo-ttl";

    @ConfigKey(ifNotPresent = "Uses " + DEFAULT_LDAP_BIND_DN)
    public static final String LDAP_BIND_DN = "ldap.bind-dn";

    @ConfigKey(ifNotPresent = "Directory endpoints answer 500")
    public static final String LDAP_BIND_PASSWORD = "ldap.bind-password";

    @ConfigKey(ifNotPresent = "Searches from the root DSE")
    public static final String LDAP_BASE_DN = "ldap.base-dn";
  }

  public static final int LDAP_POOL_SIZE = 5;
  public static final String DEFAULT_LDAP_BIND_DN = "uid=zimbra,cn=admins,cn=zimbra";

  public static final String AUTH_TOKEN_KEY = "ZM_AUTH_TOKEN";

  public static final class ZimbraAttributes {
    private ZimbraAttributes() {}

    public static final String DISPLAY_NAME = "displayName";
    public static final String ID = "zimbraId";
    public static final String ACCOUNT_STATUS = "zimbraAccountStatus";
    public static final String IS_EXTERNAL_VIRTUAL_ACCOUNT = "zimbraIsExternalVirtualAccount";
    public static final String MAIL = "mail";
    public static final String COMMON_NAME = "cn";
    public static final String COS_ID = "zimbraCOSId";
    public static final String DOMAIN_NAME = "zimbraDomainName";
    public static final String DOMAIN_DEFAULT_COS_ID = "zimbraDomainDefaultCOSId";
  }

  public static final class ZimbraObjectClasses {
    private ZimbraObjectClasses() {}

    public static final String OBJECT_CLASS = "objectClass";
    public static final String ACCOUNT = "zimbraAccount";
    public static final String DOMAIN = "zimbraDomain";
    public static final String COS = "zimbraCOS";
    public static final String DEFAULT_COS_NAME = "default";
  }

  public static final class ZimbraPreferences {
    private ZimbraPreferences() {}

    public static final String LOCALE = "zimbraPrefLocale";
  }

  public static final int MAX_BATCH_USER_IDS = 100;
}
