package com.zextras.carbonio.user_management.rights;

import static org.assertj.core.api.Assertions.assertThat;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.zextras.carbonio.user_management.ldap.LdapDirectory;
import com.zextras.carbonio.user_management.rights.GroupMembershipService.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GroupMembershipServiceTest {

  private static final String BASE = "dc=root";
  private static final String DIRECT_MEMBER = "a0000000-0000-0000-0000-000000000001";
  private static final String NESTED_MEMBER = "a0000000-0000-0000-0000-000000000002";
  private static final String DYNAMIC_MEMBER = "a0000000-0000-0000-0000-000000000003";
  private static final String NON_MEMBER = "a0000000-0000-0000-0000-000000000004";
  private static final String ADMINS_DL = "g0000000-0000-0000-0000-000000000001";
  private static final String OUTER_DL = "g0000000-0000-0000-0000-000000000002";
  private static final String DYNAMIC_GROUP = "g0000000-0000-0000-0000-000000000003";

  private InMemoryDirectoryServer server;
  private GroupMembershipService service;

  @BeforeEach
  void setUp() throws Exception {
    InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(BASE, "cn=zimbra");
    config.setSchema(null);
    server = new InMemoryDirectoryServer(config);
    server.startListening();
    server.add("dn: " + BASE, "objectClass: top", "objectClass: domain", "dc: root");
    server.add(
        "dn: cn=zimbra", "objectClass: top", "objectClass: organizationalRole", "cn: zimbra");

    account(DIRECT_MEMBER, "direct@d1.io");
    account(NESTED_MEMBER, "nested@d1.io");
    account(DYNAMIC_MEMBER, "dynamic@d1.io", "zimbraMemberOf: " + DYNAMIC_GROUP);
    account(NON_MEMBER, "outsider@d1.io");

    server.add(
        "dn: cn=__users_admins," + BASE,
        "objectClass: zimbraDistributionList",
        "cn: __users_admins",
        "zimbraId: " + ADMINS_DL,
        "mail: __users_admins@d1.io",
        "zimbraMailAlias: admins@d1.io",
        "zimbraMailForwardingAddress: direct@d1.io",
        "zimbraMailForwardingAddress: inner@d1.io");
    server.add(
        "dn: cn=inner," + BASE,
        "objectClass: zimbraDistributionList",
        "cn: inner",
        "zimbraId: " + OUTER_DL,
        "mail: inner@d1.io",
        "zimbraMailForwardingAddress: nested@d1.io");
    server.add(
        "dn: cn=dyn," + BASE,
        "objectClass: zimbraGroup",
        "cn: dyn",
        "zimbraId: " + DYNAMIC_GROUP,
        "mail: dyn@d1.io");

    service = new GroupMembershipService(new LdapDirectory(server.getConnectionPool(2), BASE));
  }

  @AfterEach
  void tearDown() {
    server.shutDown(true);
  }

  @Test
  void directStaticMemberIsDirect() {
    assertThat(service.membership(DIRECT_MEMBER, "__users_admins@d1.io"))
        .isEqualTo(new Result(true, true));
  }

  @Test
  void nestedStaticMemberIsNotDirect() {
    assertThat(service.membership(NESTED_MEMBER, "__users_admins@d1.io"))
        .isEqualTo(new Result(true, false));
  }

  @Test
  void directMemberOfTheInnerListIsDirectThere() {
    assertThat(service.membership(NESTED_MEMBER, "inner@d1.io")).isEqualTo(new Result(true, true));
  }

  @Test
  void dynamicGroupMemberIsNotDirect() {
    assertThat(service.membership(DYNAMIC_MEMBER, "dyn@d1.io")).isEqualTo(new Result(true, false));
  }

  @Test
  void matchesTheGroupById() {
    assertThat(service.membership(DIRECT_MEMBER, ADMINS_DL)).isEqualTo(new Result(true, true));
    assertThat(service.membership(DIRECT_MEMBER, ADMINS_DL.toUpperCase()))
        .isEqualTo(new Result(true, true));
  }

  @Test
  void matchesTheGroupByAliasAddress() {
    assertThat(service.membership(DIRECT_MEMBER, "admins@d1.io")).isEqualTo(new Result(true, true));
  }

  @Test
  void addressMatchIsCaseInsensitive() {
    assertThat(service.membership(DIRECT_MEMBER, "__Users_Admins@D1.IO"))
        .isEqualTo(new Result(true, true));
    assertThat(service.membership(DIRECT_MEMBER, "  admins@d1.io  "))
        .isEqualTo(new Result(true, true));
  }

  @Test
  void nonMemberIsNotAMember() {
    assertThat(service.membership(NON_MEMBER, "__users_admins@d1.io"))
        .isEqualTo(new Result(false, false));
  }

  @Test
  void unknownAccountIsNotAMember() {
    assertThat(service.membership("ffffffff-0000-0000-0000-000000000000", "__users_admins@d1.io"))
        .isEqualTo(new Result(false, false));
  }

  @Test
  void unknownGroupIsNotAMember() {
    assertThat(service.membership(DIRECT_MEMBER, "nobody@d1.io"))
        .isEqualTo(new Result(false, false));
    assertThat(service.membership(DIRECT_MEMBER, "unknown-id")).isEqualTo(new Result(false, false));
  }

  private void account(String id, String name, String... extra) throws Exception {
    String[] lines = new String[5 + extra.length];
    lines[0] = "dn: uid=" + name.replace('@', '_') + "," + BASE;
    lines[1] = "objectClass: zimbraAccount";
    lines[2] = "zimbraId: " + id;
    lines[3] = "zimbraMailDeliveryAddress: " + name;
    lines[4] = "uid: " + name.replace('@', '_');
    System.arraycopy(extra, 0, lines, 5, extra.length);
    server.add(lines);
  }
}
