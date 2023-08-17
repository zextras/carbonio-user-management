package com.zextras.carbonio.user_management.exceptions;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * Maps the {@link ServiceException} exception into a jax-rs {@link Response} with an
 * {@link Status#INTERNAL_SERVER_ERROR} status code.
 */
@Provider
public class ServiceExceptionMapper implements ExceptionMapper<ServiceException> {

  @Override
  public Response toResponse(ServiceException exception) {
    return Response.status(Status.INTERNAL_SERVER_ERROR).entity(exception.getMessage()).build();
  }
}
