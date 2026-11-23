// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.rest;

import static com.zextras.carbonio.user_management.UserManagementServiceConfig.AUTH_TOKEN_KEY;
import static com.zextras.carbonio.user_management.UserManagementServiceConfig.MAX_BATCH_USER_IDS;

import com.zextras.carbonio.user_management.ldap.DirectoryException;
import com.zextras.carbonio.user_management.rest.dto.MyselfDto;
import com.zextras.carbonio.user_management.rest.dto.UserInfoDto;
import com.zextras.carbonio.user_management.service.UserService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.function.Supplier;
import org.jboss.resteasy.reactive.RestResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Response types are {@link RestResponse}&lt;T&gt; (RESTEasy Reactive's typed response) rather than
 * a raw {@link Response} so that the emitted OpenAPI spec (and the REST SDK generated from it)
 * carries a real response schema on every 200 - SmallRye OpenAPI reads the {@code T} type argument
 * straight off the method signature, no {@code @APIResponse}/{@code @Schema} annotations needed.
 * Every method follows the same shape: a typed {@code RestResponse.ok(...)} on success and a
 * bodiless {@code RestResponse.status(...)} for the non-2xx cases - nothing is ever thrown, and no
 * response ever carries an ad hoc error entity. A directory failure is a 500, never a 401 or 404.
 */
@Path("/internal/users")
@Produces(MediaType.APPLICATION_JSON)
public class UserResource {

  private static final Logger logger = LoggerFactory.getLogger(UserResource.class);

  private final UserService userService;

  @Inject
  public UserResource(UserService userService) {
    this.userService = userService;
  }

  /**
   * {@code bypassCache=true} forces the token to be re-validated against LDAP instead of being
   * answered from {@code UserMyselfCache}. Callers that must not keep honouring a session revoked
   * meanwhile (password change, "end all sessions") need it, because an entry lives for the whole
   * remaining token lifetime when {@code cache.usermyself-ttl} is unset. It is deliberately a
   * boolean query parameter and not {@code Cache-Control: no-cache}: the generated SDK turns a
   * header parameter into a second {@code String} argument next to the token, which swaps silently
   * at the call site, while a boolean cannot. The fresh result is still written back to the cache -
   * this is a per-request read bypass, not a cache disable.
   */
  @GET
  @Path("/myself")
  public RestResponse<MyselfDto> getMyself(
      @HeaderParam(AUTH_TOKEN_KEY) String token, @QueryParam("bypassCache") boolean bypassCache) {
    if (token == null || token.isBlank()) {
      return RestResponse.status(Response.Status.UNAUTHORIZED);
    }
    return handle(
        () ->
            userService
                .getUserMyself(token, bypassCache)
                .map(r -> RestResponse.ok(MyselfDto.from(r)))
                .orElseGet(() -> RestResponse.status(Response.Status.UNAUTHORIZED)));
  }

  @GET
  @Path("/id/{userId}")
  public RestResponse<UserInfoDto> getById(@PathParam("userId") String userId) {
    return handle(
        () ->
            userService
                .getUserById(userId)
                .map(u -> RestResponse.ok(UserInfoDto.from(u)))
                .orElseGet(() -> RestResponse.status(Response.Status.NOT_FOUND)));
  }

  @GET
  @Path("/email/{email}")
  public RestResponse<UserInfoDto> getByEmail(@PathParam("email") String email) {
    return handle(
        () -> {
          try {
            return userService
                .getUserByEmail(email)
                .map(u -> RestResponse.ok(UserInfoDto.from(u)))
                .orElseGet(() -> RestResponse.status(Response.Status.NOT_FOUND));
          } catch (IllegalArgumentException e) {
            return RestResponse.status(Response.Status.NOT_FOUND);
          }
        });
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public RestResponse<List<UserInfoDto>> getUsers(List<String> userIds) {
    if (userIds == null || userIds.size() > MAX_BATCH_USER_IDS) {
      return RestResponse.status(Response.Status.BAD_REQUEST);
    }
    return handle(
        () ->
            RestResponse.ok(
                userService.getUsers(userIds).stream().map(UserInfoDto::from).toList()));
  }

  private static <T> RestResponse<T> handle(Supplier<RestResponse<T>> action) {
    try {
      return action.get();
    } catch (DirectoryException e) {
      logger.error("Directory failure", e);
      return RestResponse.status(Response.Status.INTERNAL_SERVER_ERROR);
    }
  }
}
