// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.config;


import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.zextras.carbonio.user_management.controllers.AuthApiController;
import com.zextras.carbonio.user_management.controllers.HealthApiController;
import com.zextras.carbonio.user_management.controllers.UsersApiController;
import com.zextras.carbonio.user_management.generated.AuthApi;
import com.zextras.carbonio.user_management.generated.AuthApiService;
import com.zextras.carbonio.user_management.generated.HealthApi;
import com.zextras.carbonio.user_management.generated.HealthApiService;
import com.zextras.carbonio.user_management.generated.UsersApi;
import com.zextras.carbonio.user_management.generated.UsersApiService;
import org.jboss.resteasy.plugins.guice.ext.RequestScopeModule;

public class UserManagementModule extends RequestScopeModule {

  @Override
  protected void configure() {
    bind(JacksonJsonProvider.class);

    bind(HealthApi.class);
    bind(HealthApiService.class).to(HealthApiController.class);

    bind(UsersApi.class);
    bind(UsersApiService.class).to(UsersApiController.class);

    bind(AuthApi.class);
    bind(AuthApiService.class).to(AuthApiController.class);
  }
}
