// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management;

import com.zextras.carbonio.quarkus.extensions.bootstrap.db.CarbonioDatabaseServiceConfig;

public final class UserManagementServiceConfig implements CarbonioDatabaseServiceConfig {

  @Override
  public String getServiceName() {
    return "carbonio-user-management";
  }

  public interface NetworkingConfig {
    String MAILBOX_HOST = "carbonio.mailbox.host";
    String MAILBOX_PORT = "carbonio.mailbox.port";
  }

  public interface ApplicationConfig {
    String CACHE_DETAILS_TTL = "cache.userdetails-ttl";
    String CACHE_USERINFO_TTL = "cache.userinfo-ttl";
  }

  public static final String AUTH_TOKEN_KEY = "ZM_AUTH_TOKEN";
}
