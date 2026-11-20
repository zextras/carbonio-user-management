// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.config.migration;

import com.zextras.carbonio.quarkus.extensions.bootstrap.setup.migration.ConfigMigration;
import java.net.URI;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Migrates networking config keys from the old layout to the new one:
 * <ul>
 *   <li>{@code carbonio.user-management.host/port} → {@code carbonio.service.host/port}</li>
 *   <li>{@code carbonio.mailbox.url} → {@code carbonio.mailbox.host} + {@code carbonio.mailbox.port}</li>
 * </ul>
 */
public class V1__MigrateNetworkingConfig extends ConfigMigration {

  @Override
  protected Map<String, BiConsumer<String, String>> networkingMigrations() {
    return Map.of(
        "carbonio.user-management.host", this::renameToService,
        "carbonio.user-management.port", this::renameToService,
        "carbonio.mailbox.url", this::splitMailboxUrl
    );
  }

  @Override
  protected Map<String, BiConsumer<String, String>> applicationMigrations() {
    return Map.of();
  }

  private void renameToService(String key, String value) {
    String newKey = key.replace("carbonio.user-management.", "carbonio.service.");
    networkingConfig.set(newKey, value);
  }

  private void splitMailboxUrl(String key, String value) {
    URI uri = URI.create(value);
    networkingConfig.set("carbonio.mailbox.host", uri.getHost());
    networkingConfig.set("carbonio.mailbox.port", String.valueOf(uri.getPort()));
  }
}
