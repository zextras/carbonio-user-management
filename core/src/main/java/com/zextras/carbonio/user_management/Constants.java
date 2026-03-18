// SPDX-FileCopyrightText: 2023 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management;

public final class Constants {

  private Constants(){}

  public static final class UserManagement {

    public static final String DEFAULT_HOST = "127.78.0.5";
    public static final int DEFAULT_PORT = 10_000;
    public static final String HOST_PROPERTY = "carbonio.user-management.host";
    public static final String PORT_PROPERTY = "carbonio.user-management.port";
  }

  public static final class Config {
    public static final class Mailbox {
      public static final String HOST_PROPERTY = "carbonio.mailbox.host";
      public static final String PORT_PROPERTY = "carbonio.mailbox.port";
      public static final int POOL_SIZE = 5;

      public static final String DEFAULT_PROTOCOL = "http";
      public static final String DEFAULT_HOST = "127.78.0.5";
      public static final int DEFAULT_PORT = 20000;

      private Mailbox() {}
    }

    public static final class Ldap {
      public static final String HOST_PROPERTY = "carbonio.ldap.host";
      public static final String PORT_PROPERTY = "carbonio.ldap.port";
      public static final String BIND_DN_PROPERTY = "carbonio.ldap.bind-dn";
      public static final String BIND_PASSWORD_PROPERTY = "carbonio.ldap.bind-password";
      public static final String BASE_DN_PROPERTY = "carbonio.ldap.base-dn";

      public static final String DEFAULT_HOST = "127.78.0.5";
      public static final int DEFAULT_PORT = 389;
      public static final String DEFAULT_BIND_DN = "uid=zimbra,cn=admins,cn=zimbra";
      public static final String DEFAULT_BASE_DN = "";
      public static final int POOL_SIZE = 5;

      private Ldap() {}
    }
  }
}
