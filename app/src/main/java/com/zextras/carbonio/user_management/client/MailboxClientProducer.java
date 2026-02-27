// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.client;

import com.zextras.carbonio.quarkus.extensions.bootstrap.NetworkingConfigService;
import com.zextras.carbonio.user_management.UserManagementServiceConfig.NetworkingConfig;
import com.zextras.mailbox.client.MailboxClient;
import com.zextras.mailbox.client.service.ServiceClient;
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
  public ServiceClient serviceClient() throws Exception {
    String host = networkingConfigService.get(NetworkingConfig.MAILBOX_HOST)
        .orElseThrow(() -> new IllegalStateException(
            "Missing required config: " + NetworkingConfig.MAILBOX_HOST));
    String port = networkingConfigService.get(NetworkingConfig.MAILBOX_PORT)
        .orElseThrow(() -> new IllegalStateException(
            "Missing required config: " + NetworkingConfig.MAILBOX_PORT));

    String mailboxUrl = "http://" + host + ":" + port;
    logger.info("Connecting to mailbox at {}", mailboxUrl);

    return new MailboxClient.Builder()
        .withServer(mailboxUrl)
        .build()
        .newServiceClientBuilder()
        .withPool(5)
        .build();
  }
}
