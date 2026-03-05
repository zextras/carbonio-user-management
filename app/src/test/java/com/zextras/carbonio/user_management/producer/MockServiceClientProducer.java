// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.client;

import com.zextras.mailbox.client.service.ServiceClient;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.mockito.Mockito;

/**
 * CDI alternative that replaces {@code MailboxClientProducer} in {@code @QuarkusTest}s.
 * Produces a Mockito mock so no real SOAP/WSDL connection is needed.
 */
@ApplicationScoped
public class MockServiceClientProducer {

  @Produces
  @Singleton
  @Alternative
  @Priority(1)
  ServiceClient serviceClient() {
    return Mockito.mock(ServiceClient.class);
  }
}
