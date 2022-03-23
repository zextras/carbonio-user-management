// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.config;

import com.google.inject.Singleton;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

@Singleton
public class UserManagementConfig {

  private final Properties properties;

  public UserManagementConfig() {
    properties = new Properties();
  }

  public void loadConfig() throws IOException {
    try {
      FileInputStream file = new FileInputStream("/etc/carbonio/user-management/config.properties");
      properties.load(file);
    } catch (FileNotFoundException e) {
      properties.load(new FileInputStream("resources/carbonio-user-management.properties"));
    }
  }

  public Properties getProperties() {
    return properties;
  }
}
