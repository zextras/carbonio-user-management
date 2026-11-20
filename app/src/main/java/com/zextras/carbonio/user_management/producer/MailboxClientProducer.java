// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.client;

import com.zextras.carbonio.quarkus.extensions.bootstrap.NetworkingConfigService;
import com.zextras.carbonio.user_management.UserManagementServiceConfig.NetworkingConfig;
import com.zextras.mailbox.client.internal.MailboxInternalApiClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class MailboxClientProducer {

  private static final Logger logger = LoggerFactory.getLogger(MailboxClientProducer.class);

  private final NetworkingConfigService networkingConfigService;

  @Inject
  public MailboxClientProducer(NetworkingConfigService networkingConfigService) {
    this.networkingConfigService = networkingConfigService;
  }

  @Produces
  @Singleton
  public MailboxInternalApiClient mailboxInternalApiClient() {
    String host = networkingConfigService.get(NetworkingConfig.MAILBOX_INTERNAL_HOST)
        .orElseThrow(() -> new IllegalStateException(
            "Missing required config: " + NetworkingConfig.MAILBOX_INTERNAL_HOST));
    String port = networkingConfigService.get(NetworkingConfig.MAILBOX_INTERNAL_PORT)
        .orElseThrow(() -> new IllegalStateException(
            "Missing required config: " + NetworkingConfig.MAILBOX_INTERNAL_PORT));

    String mailboxInternalUrl = "http://" + host + ":" + port;
    logger.info("Connecting to mailbox internal API at {}", mailboxInternalUrl);

    return new MailboxInternalApiClient(mailboxInternalUrl);
  }
}
