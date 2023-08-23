// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.controllers;

import com.google.inject.Inject;
import com.zextras.carbonio.user_management.exceptions.ServiceException;
import com.zextras.carbonio.user_management.generated.NotFoundException;
import com.zextras.carbonio.user_management.generated.UsersApiService;
import com.zextras.carbonio.user_management.services.UserService;
import com.zextras.carbonio.user_management.utilities.CookieParser;
import java.util.List;
import java.util.Map;
import javax.enterprise.context.RequestScoped;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;

@RequestScoped
@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen", date = "2021-12-22T09:50:40.665594+01:00[Europe/Rome]")
public class UsersApiController implements UsersApiService {

  private final int MAX_USER_IDS = 10;
  private final UserService userService;

  @Inject
  public UsersApiController(UserService usersService) {
    this.userService = usersService;
  }

  public Response getUserInfoByEmail(
    String cookie,
    String userEmail,
    SecurityContext securityContext
  ) {
    Map<String, String> cookies = CookieParser.getCookies(cookie);

    return (cookies.containsKey("ZM_AUTH_TOKEN"))
      ? userService.getInfoByEmail(userEmail, cookies.get("ZM_AUTH_TOKEN"))
      : Response.status(Status.BAD_REQUEST).build();
  }

  public Response getUserInfoById(
    String cookie,
    String userId,
    SecurityContext securityContext
  ) {
    Map<String, String> cookies = CookieParser.getCookies(cookie);

    return (cookies.containsKey("ZM_AUTH_TOKEN"))
      ? userService.getInfoById(userId, cookies.get("ZM_AUTH_TOKEN"))
      : Response.status(Status.BAD_REQUEST).build();
  }

  @Override
  public Response getUsersInfo(String cookie, List<String> userIds, SecurityContext securityContext)
    throws NotFoundException {
    Map<String, String> cookies = CookieParser.getCookies(cookie);

    return (cookies.containsKey("ZM_AUTH_TOKEN") && (!userIds.isEmpty() && userIds.size() <= MAX_USER_IDS))
      ? userService.getUsers(userIds, cookies.get("ZM_AUTH_TOKEN"))
      : Response.status(Status.BAD_REQUEST).build();
  }

  @Override
  public Response getMyselfByCookie(String cookie, SecurityContext securityContext) {
    Map<String, String> cookies = CookieParser.getCookies(cookie);

    if (cookies.containsKey("ZM_AUTH_TOKEN")) {
      return userService
        .getInfoByToken(cookies.get("ZM_AUTH_TOKEN"))
        .map(userMyself -> Response.ok().entity(userMyself).build())
        .orElse(Response.status(Status.NOT_FOUND).build());
    }

    return Response.status(Status.BAD_REQUEST).build();
  }
}
