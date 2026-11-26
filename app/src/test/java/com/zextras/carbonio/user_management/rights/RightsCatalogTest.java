package com.zextras.carbonio.user_management.rights;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RightsCatalogTest {

  private static RightsCatalog catalog;

  @BeforeAll
  static void load() {
    catalog = new RightsCatalog();
  }

  @Test
  void loadsAllAttributeRights() {
    Right.Attr modifyAccount = (Right.Attr) catalog.find("modifyAccount").orElseThrow();
    assertThat(modifyAccount.set()).isTrue();
    assertThat(modifyAccount.allAttrs()).isTrue();
    assertThat(modifyAccount.executableOn("account")).isTrue();
  }

  @Test
  void loadsTheGeneratedDomainAdminAttributeSet() {
    Right.Attr right =
        (Right.Attr) catalog.find("setDomainAdminAccountAndCalendarResourceAttrs").orElseThrow();
    assertThat(right.attrs())
        .contains("zimbramailquota", "carbonioFeatureFilesEnabled".toLowerCase(), "zimbracosid")
        .doesNotContain("zimbraisadminaccount", "zimbramailhost");
  }

  @Test
  void expandsCombosTransitively() {
    Right.Combo domainAdminRights = (Right.Combo) catalog.find("domainAdminRights").orElseThrow();
    assertThat(catalog.presetRights(domainAdminRights))
        .contains((Right.Preset) catalog.find("getMailboxInfo").orElseThrow());
    assertThat(catalog.attrRights(domainAdminRights))
        .contains(
            (Right.Attr)
                catalog.find("setDomainAdminAccountAndCalendarResourceAttrs").orElseThrow());
  }

  @Test
  void parsesInlineAttributeRights() {
    Right.Attr inline = (Right.Attr) catalog.find("set.account.zimbraMailQuota").orElseThrow();
    assertThat(inline.set()).isTrue();
    assertThat(inline.targetTypes()).containsExactly("account");
    assertThat(inline.attrs()).containsExactly("zimbramailquota");

    assertThat(catalog.find("set.account.notAnAttribute")).isEmpty();
    assertThat(catalog.find("set.global.zimbraMailQuota")).isEmpty();
    assertThat(catalog.find("put.account.zimbraMailQuota")).isEmpty();
    assertThat(catalog.find("set.account")).isEmpty();
  }

  @Test
  void knowsTheQuotaRights() {
    assertThat(catalog.find("setAccountQuota")).get().isInstanceOf(Right.Preset.class);
    assertThat(catalog.find("getDomainQuotaUsage").orElseThrow().executableOn("domain")).isTrue();
    assertThat(catalog.find("configureQuota").orElseThrow().executableOn("cos")).isTrue();
  }

  @Test
  void listsCheckableRightsWithoutCombos() {
    assertThat(catalog.checkableOn("account"))
        .extracting(Right::name)
        .contains("modifyAccount", "setAccountQuota", "getMailboxInfo")
        .doesNotContain("domainAdminAccountRights", "getDomain");
    assertThat(catalog.description(catalog.find("modifyAccount").orElseThrow()))
        .isEqualTo("modify all account attributes");
  }

  @Test
  void unknownRightsAreAbsent() {
    assertThat(catalog.find("noSuchRight")).isEmpty();
    assertThat(catalog.find(null)).isEmpty();
  }
}
