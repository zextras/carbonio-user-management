// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management;

import com.zextras.carbonio.quarkus.extensions.bootstrap.ConfigKey;

public final class UserManagementServiceConfig {

  private UserManagementServiceConfig() {}

  public static final class NetworkingConfig {
    private NetworkingConfig() {}

    @ConfigKey(ifNotPresent = "The service does not start")
    public static final String LDAP_URL = "carbonio.ldap.url";

    @ConfigKey(ifNotPresent = "No StartTLS on ldap:// URLs")
    public static final String LDAP_STARTTLS = "carbonio.ldap.starttls";
  }

  public static final class ApplicationConfig {
    private ApplicationConfig() {}

    @ConfigKey(
        description =
            "Seconds a /myself entry stays cached, never longer than the remaining token validity;"
                + " 0 disables the cache")
    public static final String CACHE_USERMYSELF_TTL = "cache.usermyself-ttl";

    @ConfigKey(description = "Seconds a user entry stays cached; 0 disables the cache")
    public static final String CACHE_USERINFO_TTL = "cache.userinfo-ttl";

    @ConfigKey(
        description = "LDAP bind DN, written by carbonio-user-management-setup",
        ifNotPresent = "The service does not start")
    public static final String LDAP_USERNAME = "ldap-username";

    @ConfigKey(
        description = "LDAP bind password, written by carbonio-user-management-setup",
        ifNotPresent = "The service does not start")
    public static final String LDAP_PASSWORD = "ldap-password";

    @ConfigKey(ifNotPresent = "Searches from the root DSE")
    public static final String LDAP_BASE_DN = "ldap.base-dn";
  }

  public static final long DEFAULT_CACHE_TTL_SECONDS = 900;
  public static final long CACHE_MAX_ENTRIES = 100_000;
  public static final int LDAP_POOL_SIZE = 5;
  public static final int LDAP_MAX_POOL_SIZE = 20;
  public static final int LDAP_CONNECT_TIMEOUT_MILLIS = 5_000;
  public static final long LDAP_RESPONSE_TIMEOUT_MILLIS = 10_000;

  public static final String AUTH_TOKEN_KEY = "ZM_AUTH_TOKEN";

  public static final class ZimbraAttributes {
    private ZimbraAttributes() {}

    public static final String DISPLAY_NAME = "displayName";
    public static final String ID = "zimbraId";
    public static final String ACCOUNT_STATUS = "zimbraAccountStatus";
    public static final String IS_EXTERNAL_VIRTUAL_ACCOUNT = "zimbraIsExternalVirtualAccount";
    public static final String IS_ADMIN_ACCOUNT = "zimbraIsAdminAccount";
    public static final String IS_DELEGATED_ADMIN_ACCOUNT = "zimbraIsDelegatedAdminAccount";
    public static final String MAIL_DELIVERY_ADDRESS = "zimbraMailDeliveryAddress";
    public static final String MAIL_ALIAS = "zimbraMailAlias";
    public static final String OLD_MAIL_ADDRESS = "zimbraOldMailAddress";
    public static final String MAIL_HOST = "zimbraMailHost";
    public static final String COMMON_NAME = "cn";
    public static final String COS_ID = "zimbraCOSId";
    public static final String DOMAIN_NAME = "zimbraDomainName";
    public static final String DOMAIN_TYPE = "zimbraDomainType";
    public static final String DOMAIN_STATUS = "zimbraDomainStatus";
    public static final String DOMAIN_ALIAS_TARGET_ID = "zimbraDomainAliasTargetId";
    public static final String DOMAIN_DEFAULT_COS_ID = "zimbraDomainDefaultCOSId";
    public static final String DOMAIN_DEFAULT_EXTERNAL_COS_ID =
        "zimbraDomainDefaultExternalUserCOSId";
    public static final String DEFAULT_DOMAIN_NAME = "zimbraDefaultDomainName";
    public static final String PUBLIC_SERVICE_HOSTNAME = "zimbraPublicServiceHostname";
    public static final String PUBLIC_SERVICE_PROTOCOL = "zimbraPublicServiceProtocol";
    public static final String PREF_LOCALE = "zimbraPrefLocale";
    public static final String LOCALE = "zimbraLocale";
    public static final String AUTH_TOKEN_KEY = "zimbraAuthTokenKey";
    public static final String AUTH_TOKENS = "zimbraAuthTokens";
    public static final String AUTH_TOKEN_VALIDITY_VALUE = "zimbraAuthTokenValidityValue";
    public static final String AUTH_TOKEN_VALIDITY_VALUE_ENABLED =
        "zimbraAuthTokenValidityValueEnabled";
    public static final String MEMBER_OF = "zimbraMemberOf";
    public static final String MAIL = "mail";
    public static final String MAIL_FORWARDING_ADDRESS = "zimbraMailForwardingAddress";
    public static final String IS_ADMIN_GROUP = "zimbraIsAdminGroup";
    public static final String ACE = "zimbraACE";
  }

  public static final class ZimbraObjectClasses {
    private ZimbraObjectClasses() {}

    public static final String OBJECT_CLASS = "objectClass";
    public static final String ACCOUNT = "zimbraAccount";
    public static final String DOMAIN = "zimbraDomain";
    public static final String COS = "zimbraCOS";
    public static final String SERVER = "zimbraServer";
    public static final String DISTRIBUTION_LIST = "zimbraDistributionList";
    public static final String GROUP = "zimbraGroup";
    public static final String DEFAULT_COS_NAME = "default";
    public static final String DEFAULT_EXTERNAL_COS_NAME = "defaultExternal";
  }

  public static final int MAX_BATCH_USER_IDS = 100;
}
