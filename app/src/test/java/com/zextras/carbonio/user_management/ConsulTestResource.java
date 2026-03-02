// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management;

import com.zextras.carbonio.quarkus.extensions.bootstrap.CarbonioServiceConfig;
import com.zextras.carbonio.quarkus.extensions.bootstrap.ConsulTestHelper;
import com.zextras.carbonio.quarkus.extensions.bootstrap.db.CarbonioDatabaseServiceConfig;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;
import org.testcontainers.consul.ConsulContainer;

public class ConsulTestResource implements QuarkusTestResourceLifecycleManager {

  private ConsulContainer consul;

  @Override
  public Map<String, String> start() {
    consul = new ConsulContainer("hashicorp/consul:1.15");
    consul.start();

    String consulHost = consul.getHost();
    int consulPort = consul.getFirstMappedPort();

    ConsulTestHelper helper = new ConsulTestHelper(consulHost, consulPort);
    String svc = "carbonio-user-management";
    helper.putValue(svc + "/" + CarbonioDatabaseServiceConfig.ApplicationConfig.DB_NAME,
        "carbonio_user_management");
    helper.putValue(svc + "/" + CarbonioDatabaseServiceConfig.ApplicationConfig.DB_USERNAME,
        "carbonio");
    helper.putValue(svc + "/" + CarbonioDatabaseServiceConfig.ApplicationConfig.DB_PASSWORD,
        "carbonio");
    helper.putValue(
        svc + "/" + UserManagementServiceConfig.ApplicationConfig.CACHE_USERINFO_TTL, "3600");

    return Map.of(
        CarbonioServiceConfig.NETWORKING_CONFIG_PREFIX
            + CarbonioServiceConfig.NetworkingConfig.SERVICE_DISCOVER_HOST, consulHost,
        CarbonioServiceConfig.NETWORKING_CONFIG_PREFIX
            + CarbonioServiceConfig.NetworkingConfig.SERVICE_DISCOVER_PORT,
            String.valueOf(consulPort)
    );
  }

  @Override
  public void stop() {
    if (consul != null) {
      consul.stop();
    }
  }
}
