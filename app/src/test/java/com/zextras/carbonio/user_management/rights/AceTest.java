package com.zextras.carbonio.user_management.rights;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AceTest {

  @Test
  void parsesAPlainGrant() {
    Ace ace = Ace.parse("d1000000-0000-0000-0000-000000000001 grp domainAdminRights").orElseThrow();
    assertThat(ace.grantee()).isEqualTo("d1000000-0000-0000-0000-000000000001");
    assertThat(ace.granteeType()).isEqualTo("grp");
    assertThat(ace.right()).isEqualTo("domainAdminRights");
    assertThat(ace.deny()).isFalse();
    assertThat(ace.subDomain()).isFalse();
    assertThat(ace.disinheritSubGroups()).isFalse();
  }

  @Test
  void parsesModifiers() {
    assertThat(Ace.parse("id usr -getAccount").orElseThrow().deny()).isTrue();
    assertThat(Ace.parse("id usr +getAccount").orElseThrow().deny()).isFalse();
    assertThat(Ace.parse("id usr *getDomain").orElseThrow().subDomain()).isTrue();
    assertThat(Ace.parse("id usr ^getAccount").orElseThrow().disinheritSubGroups()).isTrue();
    assertThat(Ace.parse("id usr -set.account.displayName").orElseThrow().right())
        .isEqualTo("set.account.displayName");
  }

  @Test
  void granteeMayContainSpaces() {
    Ace ace = Ace.parse("cn=Some Group,ou=x egp -getAccount").orElseThrow();
    assertThat(ace.grantee()).isEqualTo("cn=Some Group,ou=x");
    assertThat(ace.granteeType()).isEqualTo("egp");
  }

  @Test
  void rejectsMalformedValues() {
    assertThat(Ace.parse(null)).isEmpty();
    assertThat(Ace.parse("onlyone")).isEmpty();
    assertThat(Ace.parse("two parts")).isEmpty();
    assertThat(Ace.parse("id usr -")).isEmpty();
  }
}
