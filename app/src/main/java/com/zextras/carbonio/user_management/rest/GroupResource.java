package com.zextras.carbonio.user_management.rest;

import com.zextras.carbonio.user_management.ldap.DirectoryException;
import com.zextras.carbonio.user_management.rest.dto.GroupMembershipResponse;
import com.zextras.carbonio.user_management.rights.GroupMembershipService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/internal/groups")
@Produces(MediaType.APPLICATION_JSON)
public class GroupResource {

  private static final Logger logger = LoggerFactory.getLogger(GroupResource.class);

  private final GroupMembershipService membershipService;

  @Inject
  public GroupResource(GroupMembershipService membershipService) {
    this.membershipService = membershipService;
  }

  @GET
  @Path("/membership")
  public RestResponse<GroupMembershipResponse> membership(
      @QueryParam("accountId") String accountId, @QueryParam("group") String group) {
    if (isBlank(accountId) || isBlank(group)) {
      return RestResponse.status(Response.Status.BAD_REQUEST);
    }
    try {
      GroupMembershipService.Result result = membershipService.membership(accountId, group);
      return RestResponse.ok(new GroupMembershipResponse(result.member(), result.direct()));
    } catch (DirectoryException e) {
      logger.error("Directory failure while checking group membership", e);
      return RestResponse.status(StatusCodes.FAILED_DEPENDENCY);
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
