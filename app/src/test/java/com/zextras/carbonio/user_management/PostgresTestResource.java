// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management;

import com.zextras.carbonio.quarkus.extensions.bootstrap.CarbonioServiceConfig;
import com.zextras.carbonio.quarkus.extensions.bootstrap.db.CarbonioDatabaseServiceConfig;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;
import org.testcontainers.containers.PostgreSQLContainer;

public class PostgresTestResource implements QuarkusTestResourceLifecycleManager {

  private PostgreSQLContainer<?> postgres;

  @Override
  public Map<String, String> start() {
    postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("carbonio_user_management")
        .withUsername("carbonio")
        .withPassword("carbonio");
    postgres.start();

    String host = postgres.getHost();
    String port = String.valueOf(postgres.getFirstMappedPort());

    return Map.of(
        CarbonioServiceConfig.NETWORKING_CONFIG_PREFIX
            + CarbonioDatabaseServiceConfig.NetworkingConfig.POSTGRESQL_HOST, host,
        CarbonioServiceConfig.NETWORKING_CONFIG_PREFIX
            + CarbonioDatabaseServiceConfig.NetworkingConfig.POSTGRESQL_PORT, port
    );
  }

  @Override
  public void stop() {
    if (postgres != null) {
      postgres.stop();
    }
  }
}
