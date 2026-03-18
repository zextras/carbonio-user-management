// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management;

import com.zextras.carbonio.quarkus.extensions.bootstrap.ConfigKey;

public final class UserManagementServiceConfig {

  private UserManagementServiceConfig() {}

  public static final class NetworkingConfig {
    private NetworkingConfig() {}
    @ConfigKey public static final String MAILBOX_HOST = "carbonio.mailbox.host";
    @ConfigKey public static final String MAILBOX_PORT = "carbonio.mailbox.port";
  }

  public static final class ApplicationConfig {
    private ApplicationConfig() {}
    @ConfigKey(ifNotPresent = "Uses remaining token validity time") public static final String CACHE_USERMYSELF_TTL = "cache.usermyself-ttl";
    @ConfigKey public static final String CACHE_USERINFO_TTL = "cache.userinfo-ttl";
  }

  public static final String AUTH_TOKEN_KEY = "ZM_AUTH_TOKEN";

  public static final class ZimbraAttributes {
    private ZimbraAttributes() {}
    public static final String DISPLAY_NAME = "displayName";
    public static final String ID = "zimbraId";
    public static final String ACCOUNT_STATUS = "zimbraAccountStatus";
    public static final String IS_EXTERNAL_VIRTUAL_ACCOUNT = "zimbraIsExternalVirtualAccount";
  }

  public static final class ZimbraPreferences {
    private ZimbraPreferences() {}
    public static final String LOCALE = "zimbraPrefLocale";
  }

  public static final int MAX_BATCH_USER_IDS = 100;

  public static final class FeatureFlags {
    private FeatureFlags() {}
    public static final String FILES_ENABLED = "carbonioFeatureFilesEnabled";
    public static final String WSC_ENABLED = "carbonioFeatureWscEnabled";
    public static final String TASKS_ENABLED = "carbonioFeatureTasksEnabled";
  }
}
