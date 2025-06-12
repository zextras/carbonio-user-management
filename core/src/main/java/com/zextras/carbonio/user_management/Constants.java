// SPDX-FileCopyrightText: 2023 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management;

public final class Constants {

  private Constants(){}

  public static final class Service {

    public static final String IP = "127.78.0.5";
    public static final int PORT = 10_000;
  }

  public static final class MailboxClient {

    private MailboxClient() {}

    public static final int POOL_SIZE = 5;
  }

  public static final class Config {
    public static final class Properties {
      public static final String MAILBOX_URL = "carbonio.mailbox.url";
    }

    public static final class MailboxService {

      public static final String PROTOCOL = "http";
      public static final String URL = "127.78.0.5";
      public static final int PORT = 20000;

      private MailboxService() {}
    }
  }
}
