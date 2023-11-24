// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.config;

import com.google.inject.Singleton;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

@Singleton
public class UserManagementConfig {

  private final Properties properties;

  public UserManagementConfig() {
    properties = new Properties();
  }

  public void loadConfig() throws IOException {
    final InputStream config = loadFromEtc()
      .or(this::loadFromCurrent)
      .or(this::loadFromResources)
      .orElseThrow(() -> new FileNotFoundException("No configuration properties file found"));

    properties.load(config);
  }

  private Optional<InputStream> loadFromEtc() {
    return loadFile("/etc/carbonio/user-management/config.properties");
  }

  private Optional<InputStream> loadFromCurrent() {
    return loadFile("resources/carbonio-user-management.properties");
  }

  private Optional<InputStream> loadFromResources() {
    return Optional.ofNullable(
      getClass().getClassLoader().getResourceAsStream("carbonio-user-management.properties"));
  }

  private Optional<InputStream> loadFile(String path) {
    try {
      return Optional.of(new FileInputStream(path));
    } catch (FileNotFoundException e) {
      return Optional.empty();
    }
  }

  public Properties getProperties() {
    return properties;
  }
}
