// SPDX-FileCopyrightText: 2023 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.controllers;

import com.zextras.carbonio.user_management.exceptions.ServiceException;
import com.zextras.carbonio.user_management.generated.NotFoundException;
import com.zextras.carbonio.user_management.generated.model.UserMyself;
import com.zextras.carbonio.user_management.services.UserService;

import java.util.List;
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

class UsersApiControllerTest {

  static UsersApiController usersApiController;
  static UserService userServiceMock;

  @BeforeAll
  static void init() {
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
    Mockito.when(userServiceMock.getMyselfByToken(token, false)).thenReturn(Optional.of(myselfMock));

    // When
    Response response =
        usersApiController.getMyselfByCookie(cookie, false, Mockito.mock(SecurityContext.class));

    // Then
    Assertions.assertThat(response.getStatus()).isEqualTo(HttpStatus.OK_200);
    Assertions.assertThat(response.getEntity())
        .isInstanceOf(UserMyself.class)
        .isEqualTo(myselfMock);
  }

  @Test
  void givenAnInvalidAuthTokenTheGetMyselfByCookieShouldReturnTheNotFoundStatusCode() {
    // Given
    String cookie = "ZM_AUTH_TOKEN=invalid-token;";
    String token = "invalid-token";

    Mockito.when(userServiceMock.getMyselfByToken(token, false)).thenReturn(Optional.empty());

    // When
    Response response =
        usersApiController.getMyselfByCookie(cookie, false, Mockito.mock(SecurityContext.class));

    // Then
    Assertions.assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND_404);
    Assertions.assertThat(response.getLength()).isEqualTo(-1);
  }

  @Test
  void givenAValidAuthTokenTheGetMyselfByCookieShouldReturnTheNotFoundStatusCode() {
    // Given
    String cookie = "ZM_AUTH_TOKEN=valid-token;";
    String token = "valid-token";

    Mockito.when(userServiceMock.getMyselfByToken(token, false)).thenThrow(ServiceException.class);

    // When
    ThrowableAssert.ThrowingCallable callable =
        () -> usersApiController.getMyselfByCookie(cookie, false, Mockito.mock(SecurityContext.class));

    // Then
    Assertions.assertThatExceptionOfType(ServiceException.class).isThrownBy(callable);
  }

  @Test
  void givenAValidAuthTokenTheGetUserInfoByIdShouldReturnTheServicesGetInfoByIdResponse() {
    // Given
    String userId = "fake_user_id";
    String cookie = "ZM_AUTH_TOKEN=valid-token;";
    String token = "valid-token";

    Response responseMock = Mockito.mock(Response.class);
    Mockito.when(userServiceMock.getInfoById(userId, token, false)).thenReturn(responseMock);

    // When
    Response response =
        usersApiController.getUserInfoById(cookie, userId, false, Mockito.mock(SecurityContext.class));

    // Then
    Assertions.assertThat(response)
        .isEqualTo(responseMock); // if ok should return exact same response
  }

  @Test
  void givenAnInvalidCookieTheGetUserInfoByIdShouldReturnTheBadRequestStatusCode() {
    // Given
    String userId = "fake_user_id";
    String cookie = "";

    // When
    Response response =
        usersApiController.getUserInfoById(cookie, userId, false, Mockito.mock(SecurityContext.class));

    // Then
    Assertions.assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST_400);
  }

  @Test
  void givenAValidAuthTokenTheGetUserInfoByEmailShouldReturnTheServicesGetInfoByEmailResponse() {
    // Given
    String userEmail = "fake_user_email";
    String cookie = "ZM_AUTH_TOKEN=valid-token;";
    String token = "valid-token";

    Response responseMock = Mockito.mock(Response.class);
    Mockito.when(userServiceMock.getInfoByEmail(userEmail, token, false)).thenReturn(responseMock);

    // When
    Response response =
        usersApiController.getUserInfoByEmail(
            cookie, userEmail, false, Mockito.mock(SecurityContext.class));

    // Then
    Assertions.assertThat(response).isEqualTo(responseMock);
  }

  @Test
  void givenAnInvalidCookieTheGetUserInfoByEmailShouldReturnTheBadRequestStatusCode() {
    // Given
    String userEmail = "fake_user_email";
    String cookie = "";

    // When
    Response response =
        usersApiController.getUserInfoByEmail(
            cookie, userEmail, false, Mockito.mock(SecurityContext.class));

    // Then
    Assertions.assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST_400);
  }

  @Test
  void givenAValidAuthTokenTheGetUsersInfoShouldReturnTheServicesGetUsersResponse()
      throws NotFoundException {
    // Given
    String[] userIds = {"fake_user_id"};
    String cookie = "ZM_AUTH_TOKEN=valid-token;";
    String token = "valid-token";

    Response responseMock = Mockito.mock(Response.class);
    Mockito.when(userServiceMock.getUsers(List.of(userIds), token, false)).thenReturn(responseMock);

    // When
    Response response =
        usersApiController.getUsersInfo(
            cookie, List.of(userIds), false, Mockito.mock(SecurityContext.class));

    // Then
    Assertions.assertThat(response).isEqualTo(responseMock);
  }

  @Test
  void givenAnInvalidCookieTheGetUsersInfoShouldReturnTheBadRequestStatusCode()
      throws NotFoundException {
    // Given
    String[] userIds = {"fake_user_id"};
    String cookie = "";

    // When
    Response response =
        usersApiController.getUsersInfo(
            cookie, List.of(userIds), false, Mockito.mock(SecurityContext.class));

    // Then
    Assertions.assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST_400);
  }
}
