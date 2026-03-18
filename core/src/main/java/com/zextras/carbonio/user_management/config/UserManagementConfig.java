// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.config;

import com.google.inject.Singleton;
import com.zextras.carbonio.user_management.Constants;
import com.zextras.carbonio.user_management.services.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

@Singleton
public class UserManagementConfig {

  private static final Logger logger = LoggerFactory.getLogger(UserManagementConfig.class);

  private final Properties properties;

  public UserManagementConfig() {
    properties = new Properties();
  }

  // Load config from files or system properties.
  // Crash if the config doesn't contain the required properties.
  public void loadConfig() {
    loadFromEtc() // the official way
      .ifPresent(config -> {
        try {
          properties.load(config);
        } catch (IOException e) {
          logger.warn("Error loading configuration file: {}", e.getMessage());
        }
      });

    properties.putAll(System.getProperties()); // the dev way, overriding existing properties
  }

  private Optional<InputStream> loadFromEtc() {
    return loadFile("/etc/carbonio/user-management/config.properties");
  }

  private Optional<InputStream> loadFile(String path) {
    try {
      return Optional.of(new FileInputStream(path));
    } catch (FileNotFoundException e) {
      return Optional.empty();
    }
  }

  public String getUserManagementHost() {
    return properties.getProperty(
        Constants.UserManagement.HOST_PROPERTY,
        Constants.UserManagement.DEFAULT_HOST);
  }

  public String getUserManagementPort() {
    return properties.getProperty(
        Constants.UserManagement.PORT_PROPERTY,
        String.valueOf(Constants.UserManagement.DEFAULT_PORT));
  }

  public String getMailboxHost() {
    return properties.getProperty(
        Constants.Config.Mailbox.HOST_PROPERTY,
        Constants.Config.Mailbox.DEFAULT_HOST);
  }

  public String getMailboxPort() {
    return properties.getProperty(
        Constants.Config.Mailbox.PORT_PROPERTY,
        String.valueOf(Constants.Config.Mailbox.DEFAULT_PORT));
  }

  public String getLdapHost() {
    return properties.getProperty(
        Constants.Config.Ldap.HOST_PROPERTY,
        Constants.Config.Ldap.DEFAULT_HOST);
  }

  public int getLdapPort() {
    return Integer.parseInt(properties.getProperty(
        Constants.Config.Ldap.PORT_PROPERTY,
        String.valueOf(Constants.Config.Ldap.DEFAULT_PORT)));
  }

  public String getLdapBindDn() {
    return properties.getProperty(
        Constants.Config.Ldap.BIND_DN_PROPERTY,
        Constants.Config.Ldap.DEFAULT_BIND_DN);
  }

  public String getLdapBindPassword() {
    return properties.getProperty(Constants.Config.Ldap.BIND_PASSWORD_PROPERTY);
  }

  public String getLdapBaseDn() {
    return properties.getProperty(
        Constants.Config.Ldap.BASE_DN_PROPERTY,
        Constants.Config.Ldap.DEFAULT_BASE_DN);
  }
}
