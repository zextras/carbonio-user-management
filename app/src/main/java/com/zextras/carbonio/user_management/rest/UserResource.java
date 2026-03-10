// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.rest;

import static com.zextras.carbonio.user_management.UserManagementServiceConfig.AUTH_TOKEN_KEY;
import static com.zextras.carbonio.user_management.UserManagementServiceConfig.MAX_BATCH_USER_IDS;

import com.zextras.carbonio.quarkus.extensions.ratelimit.RateLimit;
import com.zextras.carbonio.user_management.rest.dto.MyselfDto;
import com.zextras.carbonio.user_management.rest.dto.UserInfoDto;
import com.zextras.carbonio.user_management.service.UserService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@RateLimit("rest")
public class UserResource {

  private final UserService userService;

  @Inject
  public UserResource(UserService userService) {
    this.userService = userService;
  }

  @GET
  @Path("/myself")
  public Response getMyself(@Context ContainerRequestContext ctx) {
    String token = (String) ctx.getProperty(AUTH_TOKEN_KEY);
    return userService.getUserMyself(token)
        .map(r -> Response.ok(MyselfDto.from(r)).build())
        .orElse(Response.status(Response.Status.UNAUTHORIZED).build());
  }

  @GET
  @Path("/id/{userId}")
  @RequiresTokenValidation
  public Response getById(
      @PathParam("userId") String userId,
      @Context ContainerRequestContext ctx
  ) {
    String token = (String) ctx.getProperty(AUTH_TOKEN_KEY);
    return userService.getUserById(userId, token)
        .map(u -> Response.ok(UserInfoDto.from(u)).build())
        .orElse(Response.status(Response.Status.NOT_FOUND).build());
  }

  @GET
  @Path("/email/{email}")
  @RequiresTokenValidation
  public Response getByEmail(
      @PathParam("email") String email,
      @Context ContainerRequestContext ctx
  ) {
    String token = (String) ctx.getProperty(AUTH_TOKEN_KEY);
    return userService.getUserByEmail(email, token)
        .map(u -> Response.ok(UserInfoDto.from(u)).build())
        .orElse(Response.status(Response.Status.NOT_FOUND).build());
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @RequiresTokenValidation
  public Response getUsers(
      List<String> userIds,
      @Context ContainerRequestContext ctx
  ) {
    if (userIds == null || userIds.size() > MAX_BATCH_USER_IDS) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("User IDs list must contain at most " + MAX_BATCH_USER_IDS + " entries")
          .build();
    }
    String token = (String) ctx.getProperty(AUTH_TOKEN_KEY);
    List<UserInfoDto> users = userService.getUsers(userIds, token).stream()
        .map(UserInfoDto::from)
        .toList();
    return Response.ok(users).build();
  }
}
