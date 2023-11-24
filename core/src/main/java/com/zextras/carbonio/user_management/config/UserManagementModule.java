// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.config;


import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.google.inject.Provides;
import com.zextras.carbonio.user_management.Constants;
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
import javax.inject.Singleton;
import org.jboss.resteasy.plugins.guice.ext.RequestScopeModule;

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
    final UserManagementConfig config = new UserManagementConfig();
    config.loadConfig();
    return config;
  }

  @Provides
  @Singleton
  public ServiceClient provideServiceClient(UserManagementConfig config) throws Exception {
    final String carbonioMailboxUrl = config.getProperties().getProperty("carbonio.mailbox.url");
    return new MailboxClient.Builder()
      .withServer(carbonioMailboxUrl)
      .build()
      .newServiceClientBuilder()
      .withPool(Constants.MailboxClient.POOL_SIZE)
      .build();
  }
}
