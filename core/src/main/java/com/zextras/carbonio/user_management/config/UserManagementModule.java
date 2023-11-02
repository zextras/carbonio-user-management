// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.config;


import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.google.inject.Provides;
import com.zextras.carbonio.user_management.controllers.AuthApiController;
import com.zextras.carbonio.user_management.controllers.HealthApiController;
import com.zextras.carbonio.user_management.controllers.UsersApiController;
import com.zextras.carbonio.user_management.exceptions.ServiceExceptionMapper;
import com.zextras.carbonio.user_management.generated.AuthApi;
import com.zextras.carbonio.user_management.generated.AuthApiService;
import com.zextras.carbonio.user_management.generated.HealthApi;
import com.zextras.carbonio.user_management.generated.HealthApiService;
import com.zextras.carbonio.user_management.generated.UsersApi;
import com.zextras.carbonio.user_management.generated.UsersApiService;
import com.zextras.mailbox.client.MailboxClient;
import com.zextras.mailbox.client.service.ServiceClient;
import org.jboss.resteasy.plugins.guice.ext.RequestScopeModule;

import javax.inject.Singleton;

public class UserManagementModule extends RequestScopeModule {

  @Override
  protected void configure() {
    bind(JacksonJsonProvider.class);
    bind(ServiceExceptionMapper.class);

    bind(HealthApi.class);
    bind(HealthApiService.class).to(HealthApiController.class);

    bind(UsersApi.class);
    bind(UsersApiService.class).to(UsersApiController.class);

    bind(AuthApi.class);
    bind(AuthApiService.class).to(AuthApiController.class);
  }

  @Provides
  @Singleton
  public UserManagementConfig provideConfig() throws Exception {
    final var config = new UserManagementConfig();
    config.loadConfig();
    return config;
  }

  @Provides
  @Singleton
  public ServiceClient proviceServiceClient(UserManagementConfig config) throws Exception {
    final var carbonioMailboxUrl = config.getProperties().getProperty("carbonio.mailbox.url");
    final var client = new MailboxClient.Builder()
        .withServer(carbonioMailboxUrl)
        .build();

    final var POOL_SIZE = 5;
    return client.newServiceClientBuilder()
        .withPool(POOL_SIZE)
        .build();
  }
}
