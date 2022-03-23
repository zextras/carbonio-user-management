// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.controllers;

import com.zextras.carbonio.user_management.generated.HealthApiService;
import javax.enterprise.context.RequestScoped;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

@RequestScoped
public class HealthApiController implements HealthApiService {

  public Response generalCheck(SecurityContext securityContext) {
    return Response.ok().build();
  }

  public Response liveCheck(SecurityContext securityContext) {
    return Response.ok().build();
  }

  public Response readyCheck(SecurityContext securityContext) {
    return Response.ok().build();
  }
}
