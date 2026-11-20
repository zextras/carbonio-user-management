package com.zextras.carbonio.user_management.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import com.zextras.carbonio.user_management.ldap.AccountDirectoryService;
import com.zextras.carbonio.user_management.ldap.DirectoryException;
import com.zextras.carbonio.user_management.record.AccountDirectory;
import com.zextras.carbonio.user_management.rest.dto.AccountDirectoryDto;
import jakarta.ws.rs.core.Response;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccountResourceTest {

  private AccountDirectoryService service;
  private AccountResource resource;

  @BeforeEach
  void setUp() {
    service = mock(AccountDirectoryService.class);
    resource = new AccountResource(service);
  }

  @Test
  void returnsOkWithDirectoryDto() {
    when(service.findByAccountId("acc-1"))
        .thenReturn(Optional.of(new AccountDirectory("acc-1", "cos-1", "dom-1")));

    Response response = resource.getDirectory("acc-1");

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getEntity())
        .isEqualTo(new AccountDirectoryDto("acc-1", "cos-1", "dom-1"));
  }

  @Test
  void returns404WhenAccountIsMissing() {
    when(service.findByAccountId("missing")).thenReturn(Optional.empty());

    Response response = resource.getDirectory("missing");

    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  void returns500WhenLdapFails() {
    when(service.findByAccountId("acc-1")).thenThrow(new DirectoryException(
        "down", new LDAPException(ResultCode.SERVER_DOWN)));

    Response response = resource.getDirectory("acc-1");

    assertThat(response.getStatus()).isEqualTo(500);
  }
}
