// SPDX-FileCopyrightText: 2023 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.controllers;

import com.zextras.carbonio.user_management.exceptions.ServiceException;
import com.zextras.carbonio.user_management.generated.model.UserMyself;
import com.zextras.carbonio.user_management.services.UserService;
import java.util.Optional;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowableAssert;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class UsersApiControllerTest {

  static UsersApiController usersApiController;
  static UserService userServiceMock;

  @BeforeAll
  static void  init() {
    userServiceMock = Mockito.mock(UserService.class);
    usersApiController = new UsersApiController(userServiceMock);
  }

  @AfterEach
  void cleanUp() {
    Mockito.reset(userServiceMock);
  }

  @Test
  void givenAValidAuthTokenTheGetMyselfByCookieShouldReturnTheOkStatusCodeAndTheUserMyselfObject() {
    // Given
    String cookie = "ZM_AUTH_TOKEN=valid-token;";
    String token = "valid-token";

    UserMyself myselfMock = Mockito.mock(UserMyself.class);
    Mockito.when(userServiceMock.getMyselfByToken(token)).thenReturn(Optional.of(myselfMock));

    // When
    Response response = usersApiController.getMyselfByCookie(cookie, Mockito.mock(SecurityContext.class));

    // Then
    Assertions.assertThat(response.getStatus()).isEqualTo(HttpStatus.OK_200);
    Assertions
      .assertThat(response.getEntity())
      .isInstanceOf(UserMyself.class)
      .isEqualTo(myselfMock);
  }

  @Test
  void givenAnInvalidAuthTokenTheGetMyselfByCookieShouldReturnTheNotFoundStatusCode() {
    // Given
    String cookie = "ZM_AUTH_TOKEN=invalid-token;";
    String token = "invalid-token";

    Mockito.when(userServiceMock.getMyselfByToken(token)).thenReturn(Optional.empty());

    // When
    Response response = usersApiController.getMyselfByCookie(cookie, Mockito.mock(SecurityContext.class));

    // Then
    Assertions.assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND_404);
    Assertions.assertThat(response.getLength()).isEqualTo(-1);
  }

  @Test
  void givenAValidTokenAndAServiceExceptionByTheUserServiceTheGetMyselfByCookieShouldThrowAServiceException() {
    // Given
    String cookie = "ZM_AUTH_TOKEN=valid-token;";
    String token = "valid-token";

    Mockito.when(userServiceMock.getMyselfByToken(token)).thenThrow(ServiceException.class);

    // When
    ThrowableAssert.ThrowingCallable callable =
      () -> usersApiController.getMyselfByCookie(cookie, Mockito.mock(SecurityContext.class));

    // Then
    Assertions.assertThatExceptionOfType(ServiceException.class).isThrownBy(callable);
  }

}
