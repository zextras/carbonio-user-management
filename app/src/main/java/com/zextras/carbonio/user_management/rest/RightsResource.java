package com.zextras.carbonio.user_management.rest;

import com.zextras.carbonio.user_management.ldap.DirectoryException;
import com.zextras.carbonio.user_management.rest.dto.AccountRightsDto;
import com.zextras.carbonio.user_management.rest.dto.RightCheckRequest;
import com.zextras.carbonio.user_management.rest.dto.RightCheckResponse;
import com.zextras.carbonio.user_management.rest.dto.RightInfoDto;
import com.zextras.carbonio.user_management.rights.RightsService;
import com.zextras.carbonio.user_management.rights.RightsService.InvalidRequestException;
import com.zextras.carbonio.user_management.rights.RightsService.InvalidTokenException;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
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

@Path("/internal/rights")
@Produces(MediaType.APPLICATION_JSON)
public class RightsResource {

  private static final Logger logger = LoggerFactory.getLogger(RightsResource.class);

  private final RightsService rightsService;

  @Inject
  public RightsResource(RightsService rightsService) {
    this.rightsService = rightsService;
  }

  @GET
  public RestResponse<List<RightInfoDto>> listRights(@QueryParam("targetType") String targetType) {
    if (isBlank(targetType)) {
      return RestResponse.status(Response.Status.BAD_REQUEST);
    }
    return handle(
        () ->
            RestResponse.ok(
                rightsService.checkableOn(targetType).stream()
                    .map(r -> RightInfoDto.from(r, rightsService.description(r)))
                    .toList()));
  }

  @POST
  @Path("/check")
  @Consumes(MediaType.APPLICATION_JSON)
  public RestResponse<RightCheckResponse> check(RightCheckRequest request) {
    if (!isValid(request)) {
      return RestResponse.status(Response.Status.BAD_REQUEST);
    }
    RightCheckRequest.Subject subject = request.subject();
    RightCheckRequest.Target target = request.target();
    return handle(
        () -> {
          boolean allowed =
              isBlank(subject.token())
                  ? rightsService.checkWithAccountId(
                      subject.accountId(),
                      request.right(),
                      target.type(),
                      target.id(),
                      target.name())
                  : rightsService.checkWithToken(
                      subject.token(), request.right(), target.type(), target.id(), target.name());
          return RestResponse.ok(new RightCheckResponse(allowed));
        });
  }

  @GET
  @Path("/accounts/{targetId}")
  public RestResponse<AccountRightsDto> accountRights(
      @PathParam("targetId") String targetId, @QueryParam("actorId") String actorId) {
    if (isBlank(actorId)) {
      return RestResponse.status(Response.Status.BAD_REQUEST);
    }
    return handle(
        () ->
            rightsService
                .accountRights(actorId, targetId)
                .map(rights -> RestResponse.ok(AccountRightsDto.from(rights)))
                .orElseGet(() -> RestResponse.status(Response.Status.NOT_FOUND)));
  }

  private static <T> RestResponse<T> handle(Supplier<RestResponse<T>> action) {
    try {
      return action.get();
    } catch (InvalidRequestException e) {
      logger.debug("Invalid rights request: {}", e.getMessage());
      return RestResponse.status(Response.Status.BAD_REQUEST);
    } catch (InvalidTokenException e) {
      return RestResponse.status(Response.Status.UNAUTHORIZED);
    } catch (DirectoryException e) {
      logger.error("Directory failure while checking rights", e);
      return RestResponse.status(StatusCodes.FAILED_DEPENDENCY);
    }
  }

  private static boolean isValid(RightCheckRequest request) {
    if (request == null
        || request.subject() == null
        || request.target() == null
        || isBlank(request.right())
        || isBlank(request.target().type())) {
      return false;
    }
    if (isBlank(request.subject().token()) == isBlank(request.subject().accountId())) {
      return false;
    }
    return isBlank(request.target().id()) || isBlank(request.target().name());
  }

  private static boolean isBlank(String value) {
    return value == null || value.isEmpty();
  }
}
