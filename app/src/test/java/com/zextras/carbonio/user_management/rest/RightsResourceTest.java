package com.zextras.carbonio.user_management.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zextras.carbonio.user_management.ldap.DirectoryException;
import com.zextras.carbonio.user_management.rest.dto.RightCheckRequest;
import com.zextras.carbonio.user_management.rights.RightsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RightsResourceTest {

  private RightsService rightsService;
  private RightsResource resource;

  @BeforeEach
  void setUp() {
    rightsService = mock(RightsService.class);
    resource = new RightsResource(rightsService);
  }

  @Test
  void listRightsReturns424WhenDirectoryFails() {
    when(rightsService.checkableOn("account")).thenThrow(new DirectoryException("ldap down", null));

    assertThat(resource.listRights("account").getStatus()).isEqualTo(424);
  }

  @Test
  void checkReturns424WhenDirectoryFails() {
    when(rightsService.checkWithAccountId(anyString(), anyString(), anyString(), any(), any()))
        .thenThrow(new DirectoryException("ldap down", null));
    RightCheckRequest request =
        new RightCheckRequest(
            new RightCheckRequest.Subject(null, "acc-1"),
            "modifyAccount",
            new RightCheckRequest.Target("account", "acc-2", null));

    assertThat(resource.check(request).getStatus()).isEqualTo(424);
  }

  @Test
  void accountRightsReturns424WhenDirectoryFails() {
    when(rightsService.accountRights("actor", "target"))
        .thenThrow(new DirectoryException("ldap down", null));

    assertThat(resource.accountRights("target", "actor").getStatus()).isEqualTo(424);
  }
}
