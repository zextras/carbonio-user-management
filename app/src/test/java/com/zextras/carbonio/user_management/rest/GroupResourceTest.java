package com.zextras.carbonio.user_management.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.zextras.carbonio.user_management.ldap.DirectoryException;
import com.zextras.carbonio.user_management.rest.dto.GroupMembershipResponse;
import com.zextras.carbonio.user_management.rights.GroupMembershipService;
import org.jboss.resteasy.reactive.RestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GroupResourceTest {

  private GroupMembershipService service;
  private GroupResource resource;

  @BeforeEach
  void setUp() {
    service = mock(GroupMembershipService.class);
    resource = new GroupResource(service);
  }

  @Test
  void returnsTheMembership() {
    when(service.membership("acc-1", "__users_admins@d1.io"))
        .thenReturn(new GroupMembershipService.Result(true, false));

    RestResponse<GroupMembershipResponse> response =
        resource.membership("acc-1", "__users_admins@d1.io");

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getEntity()).isEqualTo(new GroupMembershipResponse(true, false));
  }

  @Test
  void returns400WhenAParameterIsMissingOrBlank() {
    assertThat(resource.membership(null, "g").getStatus()).isEqualTo(400);
    assertThat(resource.membership("acc-1", null).getStatus()).isEqualTo(400);
    assertThat(resource.membership("", "g").getStatus()).isEqualTo(400);
    assertThat(resource.membership("acc-1", "  ").getStatus()).isEqualTo(400);
    verifyNoInteractions(service);
  }

  @Test
  void returns424WhenDirectoryFails() {
    when(service.membership("acc-1", "g")).thenThrow(new DirectoryException("ldap down", null));

    assertThat(resource.membership("acc-1", "g").getStatus()).isEqualTo(424);
  }
}
