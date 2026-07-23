// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.rest;

import static com.zextras.carbonio.user_management.UserManagementServiceConfig.AUTH_TOKEN_KEY;
import static com.zextras.carbonio.user_management.UserManagementServiceConfig.MAX_BATCH_USER_IDS;

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
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.jboss.resteasy.reactive.RestResponse;

/**
 * Response types are {@link RestResponse}&lt;T&gt; (RESTEasy Reactive's typed response) rather
 * than a raw {@link Response} so that the emitted OpenAPI spec (and the REST SDK generated from
 * it) carries a real response schema on every 200 - SmallRye OpenAPI reads the {@code T} type
 * argument straight off the method signature, no {@code @APIResponse}/{@code @Schema} annotations
 * needed. The one case that legitimately can't stay typed ({@code getUsers}'s validation-failure
 * 400, whose entity is a plain error string, not a {@code List<UserInfoDto>}) is expressed as a
 * thrown {@link WebApplicationException} carrying its own {@link Response} instead, so the
 * method's declared success type never has to widen to {@code Object}.
 */
@Path("/internal/users")
@Produces(MediaType.APPLICATION_JSON)
public class UserResource {

  private final UserService userService;

  @Inject
  public UserResource(UserService userService) {
    this.userService = userService;
  }

  @GET
  @Path("/myself")
  @RequiresToken
  public RestResponse<MyselfDto> getMyself(@Context ContainerRequestContext ctx) {
    String token = (String) ctx.getProperty(AUTH_TOKEN_KEY);
    return userService.getUserMyself(token)
        .map(r -> RestResponse.ok(MyselfDto.from(r)))
        .orElseGet(() -> RestResponse.status(Response.Status.UNAUTHORIZED));
  }

  @GET
  @Path("/id/{userId}")
  public RestResponse<UserInfoDto> getById(@PathParam("userId") String userId) {
    return userService.getUserById(userId)
        .map(u -> RestResponse.ok(UserInfoDto.from(u)))
        .orElseGet(() -> RestResponse.status(Response.Status.NOT_FOUND));
  }

  @GET
  @Path("/email/{email}")
  public RestResponse<UserInfoDto> getByEmail(@PathParam("email") String email) {
    return userService.getUserByEmail(email)
        .map(u -> RestResponse.ok(UserInfoDto.from(u)))
        .orElseGet(() -> RestResponse.status(Response.Status.NOT_FOUND));
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public RestResponse<List<UserInfoDto>> getUsers(List<String> userIds) {
    if (userIds == null || userIds.size() > MAX_BATCH_USER_IDS) {
      throw new WebApplicationException(
          Response.status(Response.Status.BAD_REQUEST)
              .entity("User IDs list must contain at most " + MAX_BATCH_USER_IDS + " entries")
              .build());
    }
    List<UserInfoDto> users = userService.getUsers(userIds).stream()
        .map(UserInfoDto::from)
        .toList();
    return RestResponse.ok(users);
  }
}
