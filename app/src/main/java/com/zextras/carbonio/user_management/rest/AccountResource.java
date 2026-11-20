package com.zextras.carbonio.user_management.rest;

import com.zextras.carbonio.user_management.ldap.AccountDirectoryService;
import com.zextras.carbonio.user_management.ldap.DirectoryException;
import com.zextras.carbonio.user_management.rest.dto.AccountDirectoryDto;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/internal/accounts")
@Produces(MediaType.APPLICATION_JSON)
public class AccountResource {

  private static final Logger logger = LoggerFactory.getLogger(AccountResource.class);

  private final AccountDirectoryService accountDirectoryService;

  @Inject
  public AccountResource(AccountDirectoryService accountDirectoryService) {
    this.accountDirectoryService = accountDirectoryService;
  }

  @GET
  @Path("/{accountId}")
  public RestResponse<AccountDirectoryDto> getDirectory(@PathParam("accountId") String accountId) {
    try {
      return accountDirectoryService
          .findByAccountId(accountId)
          .map(d -> RestResponse.ok(AccountDirectoryDto.from(d)))
          .orElseGet(() -> RestResponse.status(Response.Status.NOT_FOUND));
    } catch (DirectoryException e) {
      logger.error("Directory lookup failed for account {}", accountId, e);
      return RestResponse.status(Response.Status.INTERNAL_SERVER_ERROR);
    }
  }
}
