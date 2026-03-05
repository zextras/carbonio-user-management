// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.service;

import static com.zextras.carbonio.user_management.UserManagementServiceConfig.FeatureFlags;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zextras.carbonio.user_management.cache.UserInfoCache;
import com.zextras.carbonio.user_management.cache.UserMyselfCache;
import com.zextras.carbonio.user_management.record.UserInfo;
import com.zextras.carbonio.user_management.record.UserMyself;
import com.zextras.carbonio.user_management.repository.UserInfoCacheRepository;
import com.zextras.carbonio.user_management.repository.UserMyselfCacheRepository;
import com.zextras.mailbox.client.service.ServiceClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import zimbra.NamedValue;
import zimbraaccount.Attr;
import zimbraaccount.GetAccountInfoResponse;
import zimbraaccount.GetInfoResponse;
import zimbraaccount.Pref;

/**
 * Tests for SOAP response mapping methods in UserService.
 * In the same package to access package-private methods.
 */
class UserServiceMappingTest {

  private UserService userService;

  @BeforeEach
  void setUp() {
    userService = new UserService(
        mock(ServiceClient.class),
        mock(UserInfoCache.class),
        mock(UserMyselfCache.class),
        mock(UserInfoCacheRepository.class),
        mock(UserMyselfCacheRepository.class));
  }

  // Helpers must be called OUTSIDE of when().thenReturn() chains to avoid Mockito
  // "UnfinishedStubbing" errors. Always assign to a variable first, then use in thenReturn().

  private static Attr attr(String name, String value) {
    Attr a = mock(Attr.class);
    when(a.getName()).thenReturn(name);
    when(a.getValue()).thenReturn(value);
    return a;
  }

  private static Pref pref(String name, String value) {
    Pref p = mock(Pref.class);
    when(p.getName()).thenReturn(name);
    when(p.getValue()).thenReturn(value);
    return p;
  }

  private static NamedValue namedValue(String name, String value) {
    NamedValue nv = mock(NamedValue.class);
    when(nv.getName()).thenReturn(name);
    when(nv.getValue()).thenReturn(value);
    return nv;
  }

  @Nested
  class MapGetInfoToUserMyselfTests {

    private GetInfoResponse responseWithAttrs(List<Attr> attrList) {
      GetInfoResponse response = mock(GetInfoResponse.class);
      GetInfoResponse.Attrs attrs = mock(GetInfoResponse.Attrs.class);
      when(attrs.getAttr()).thenReturn(attrList);
      when(response.getAttrs()).thenReturn(attrs);
      when(response.getName()).thenReturn("user@example.com");
      return response;
    }

    @Test
    void mapsAllBasicAttributes() {
      Attr idAttr = attr("zimbraId", "uid-123");
      Attr nameAttr = attr("displayName", "John Doe");
      GetInfoResponse response = responseWithAttrs(List.of(idAttr, nameAttr));
      when(response.getPublicURL()).thenReturn("example.com");

      UserMyself result = userService.mapGetInfoToUserMyself(response);

      assertThat(result.userId()).isEqualTo("uid-123");
      assertThat(result.fullName()).isEqualTo("John Doe");
      assertThat(result.email()).isEqualTo("user@example.com");
      assertThat(result.domain()).isEqualTo("example.com");
      assertThat(result.status()).isEqualTo("ACTIVE");
      assertThat(result.type()).isEqualTo("INTERNAL");
      assertThat(result.locale()).isEqualTo("en");
      assertThat(result.featureList()).isEmpty();
    }

    @Test
    void mapsGuestUser() {
      Attr idAttr = attr("zimbraId", "uid-guest");
      Attr extAttr = attr("zimbraIsExternalVirtualAccount", "TRUE");
      GetInfoResponse response = responseWithAttrs(List.of(idAttr, extAttr));

      UserMyself result = userService.mapGetInfoToUserMyself(response);

      assertThat(result.type()).isEqualTo("GUEST");
    }

    @Test
    void mapsInternalWhenExternalVirtualAccountIsFalse() {
      Attr idAttr = attr("zimbraId", "uid-1");
      Attr extAttr = attr("zimbraIsExternalVirtualAccount", "FALSE");
      GetInfoResponse response = responseWithAttrs(List.of(idAttr, extAttr));

      UserMyself result = userService.mapGetInfoToUserMyself(response);

      assertThat(result.type()).isEqualTo("INTERNAL");
    }

    @Test
    void mapsAccountStatusUppercased() {
      Attr idAttr = attr("zimbraId", "uid-1");
      Attr statusAttr = attr("zimbraAccountStatus", "closed");
      GetInfoResponse response = responseWithAttrs(List.of(idAttr, statusAttr));

      UserMyself result = userService.mapGetInfoToUserMyself(response);

      assertThat(result.status()).isEqualTo("CLOSED");
    }

    @Test
    void mapsLocaleFromPrefs() {
      Attr idAttr = attr("zimbraId", "uid-1");
      GetInfoResponse response = responseWithAttrs(List.of(idAttr));
      GetInfoResponse.Prefs prefs = mock(GetInfoResponse.Prefs.class);
      Pref localePref = pref("zimbraPrefLocale", "it");
      when(prefs.getPref()).thenReturn(List.of(localePref));
      when(response.getPrefs()).thenReturn(prefs);

      UserMyself result = userService.mapGetInfoToUserMyself(response);

      assertThat(result.locale()).isEqualTo("it");
    }

    @Test
    void mapsLocaleWithUnderscoreFormat() {
      Attr idAttr = attr("zimbraId", "uid-1");
      GetInfoResponse response = responseWithAttrs(List.of(idAttr));
      GetInfoResponse.Prefs prefs = mock(GetInfoResponse.Prefs.class);
      Pref localePref = pref("zimbraPrefLocale", "pt_BR");
      when(prefs.getPref()).thenReturn(List.of(localePref));
      when(response.getPrefs()).thenReturn(prefs);

      UserMyself result = userService.mapGetInfoToUserMyself(response);

      assertThat(result.locale()).isEqualTo("pt_BR");
    }

    @Test
    void defaultsToEnglishWhenPrefsNull() {
      Attr idAttr = attr("zimbraId", "uid-1");
      GetInfoResponse response = responseWithAttrs(List.of(idAttr));
      when(response.getPrefs()).thenReturn(null);

      UserMyself result = userService.mapGetInfoToUserMyself(response);

      assertThat(result.locale()).isEqualTo("en");
    }

    @Test
    void defaultsToEnglishWhenLocaleNotInPrefs() {
      Attr idAttr = attr("zimbraId", "uid-1");
      GetInfoResponse response = responseWithAttrs(List.of(idAttr));
      GetInfoResponse.Prefs prefs = mock(GetInfoResponse.Prefs.class);
      Pref otherPref = pref("otherPref", "value");
      when(prefs.getPref()).thenReturn(List.of(otherPref));
      when(response.getPrefs()).thenReturn(prefs);

      UserMyself result = userService.mapGetInfoToUserMyself(response);

      assertThat(result.locale()).isEqualTo("en");
    }

    @Test
    void mapsFeatureFlags() {
      Attr idAttr = attr("zimbraId", "uid-1");
      Attr filesAttr = attr(FeatureFlags.FILES_ENABLED, "TRUE");
      Attr wscAttr = attr(FeatureFlags.WSC_ENABLED, "FALSE");
      Attr tasksAttr = attr(FeatureFlags.TASKS_ENABLED, "true");
      GetInfoResponse response = responseWithAttrs(
          List.of(idAttr, filesAttr, wscAttr, tasksAttr));

      UserMyself result = userService.mapGetInfoToUserMyself(response);

      assertThat(result.featureList()).hasSize(3);
      assertThat(result.featureList()).containsEntry(FeatureFlags.FILES_ENABLED, true);
      assertThat(result.featureList()).containsEntry(FeatureFlags.WSC_ENABLED, false);
      assertThat(result.featureList()).containsEntry(FeatureFlags.TASKS_ENABLED, true);
    }

    @Test
    void handlesNullAttrs() {
      GetInfoResponse response = mock(GetInfoResponse.class);
      when(response.getAttrs()).thenReturn(null);
      when(response.getName()).thenReturn("user@example.com");

      UserMyself result = userService.mapGetInfoToUserMyself(response);

      assertThat(result.userId()).isNull();
      assertThat(result.email()).isEqualTo("user@example.com");
      assertThat(result.status()).isEqualTo("ACTIVE");
      assertThat(result.type()).isEqualTo("INTERNAL");
    }

    @Test
    void handlesNullAccountStatus() {
      Attr idAttr = attr("zimbraId", "uid-1");
      Attr statusAttr = attr("zimbraAccountStatus", null);
      GetInfoResponse response = responseWithAttrs(List.of(idAttr, statusAttr));

      UserMyself result = userService.mapGetInfoToUserMyself(response);

      assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    void handlesNullExternalVirtualAccount() {
      Attr idAttr = attr("zimbraId", "uid-1");
      Attr extAttr = attr("zimbraIsExternalVirtualAccount", null);
      GetInfoResponse response = responseWithAttrs(List.of(idAttr, extAttr));

      UserMyself result = userService.mapGetInfoToUserMyself(response);

      assertThat(result.type()).isEqualTo("INTERNAL");
    }

    @Test
    void unknownAttributesAreIgnored() {
      Attr idAttr = attr("zimbraId", "uid-1");
      Attr unknownAttr = attr("unknownAttr", "someValue");
      Attr anotherAttr = attr("anotherUnknown", "anotherValue");
      GetInfoResponse response = responseWithAttrs(
          List.of(idAttr, unknownAttr, anotherAttr));

      UserMyself result = userService.mapGetInfoToUserMyself(response);

      assertThat(result.userId()).isEqualTo("uid-1");
      assertThat(result.featureList()).isEmpty();
    }
  }

  @Nested
  class MapGetAccountInfoToUserInfoTests {

    private GetAccountInfoResponse responseWithAttrs(List<NamedValue> attrList) {
      GetAccountInfoResponse response = mock(GetAccountInfoResponse.class);
      when(response.getAttr()).thenReturn(attrList);
      when(response.getName()).thenReturn("user@example.com");
      when(response.getPublicURL()).thenReturn("example.com");
      return response;
    }

    @Test
    void mapsAllBasicAttributes() {
      NamedValue idAttr = namedValue("zimbraId", "uid-123");
      NamedValue nameAttr = namedValue("displayName", "Jane Doe");
      GetAccountInfoResponse response = responseWithAttrs(List.of(idAttr, nameAttr));

      UserInfo result = userService.mapGetAccountInfoToUserInfo(response);

      assertThat(result.userId()).isEqualTo("uid-123");
      assertThat(result.fullName()).isEqualTo("Jane Doe");
      assertThat(result.email()).isEqualTo("user@example.com");
      assertThat(result.domain()).isEqualTo("example.com");
      assertThat(result.status()).isEqualTo("ACTIVE");
      assertThat(result.type()).isEqualTo("INTERNAL");
    }

    @Test
    void mapsGuestUser() {
      NamedValue idAttr = namedValue("zimbraId", "uid-guest");
      NamedValue extAttr = namedValue("zimbraIsExternalVirtualAccount", "TRUE");
      GetAccountInfoResponse response = responseWithAttrs(List.of(idAttr, extAttr));

      UserInfo result = userService.mapGetAccountInfoToUserInfo(response);

      assertThat(result.type()).isEqualTo("GUEST");
    }

    @Test
    void mapsAccountStatusUppercased() {
      NamedValue idAttr = namedValue("zimbraId", "uid-1");
      NamedValue statusAttr = namedValue("zimbraAccountStatus", "locked");
      GetAccountInfoResponse response = responseWithAttrs(List.of(idAttr, statusAttr));

      UserInfo result = userService.mapGetAccountInfoToUserInfo(response);

      assertThat(result.status()).isEqualTo("LOCKED");
    }

    @Test
    void handlesNullAttrList() {
      GetAccountInfoResponse response = mock(GetAccountInfoResponse.class);
      when(response.getAttr()).thenReturn(null);
      when(response.getName()).thenReturn("user@example.com");
      when(response.getPublicURL()).thenReturn("example.com");

      UserInfo result = userService.mapGetAccountInfoToUserInfo(response);

      assertThat(result.userId()).isNull();
      assertThat(result.email()).isEqualTo("user@example.com");
      assertThat(result.status()).isEqualTo("ACTIVE");
      assertThat(result.type()).isEqualTo("INTERNAL");
    }

    @Test
    void handlesNullAccountStatus() {
      NamedValue idAttr = namedValue("zimbraId", "uid-1");
      NamedValue statusAttr = namedValue("zimbraAccountStatus", null);
      GetAccountInfoResponse response = responseWithAttrs(List.of(idAttr, statusAttr));

      UserInfo result = userService.mapGetAccountInfoToUserInfo(response);

      assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    void handlesNullExternalVirtualAccount() {
      NamedValue idAttr = namedValue("zimbraId", "uid-1");
      NamedValue extAttr = namedValue("zimbraIsExternalVirtualAccount", null);
      GetAccountInfoResponse response = responseWithAttrs(List.of(idAttr, extAttr));

      UserInfo result = userService.mapGetAccountInfoToUserInfo(response);

      assertThat(result.type()).isEqualTo("INTERNAL");
    }

    @Test
    void defaultsWithMinimalAttributes() {
      GetAccountInfoResponse response = mock(GetAccountInfoResponse.class);
      when(response.getAttr()).thenReturn(List.of());
      when(response.getName()).thenReturn("user@example.com");
      when(response.getPublicURL()).thenReturn(null);

      UserInfo result = userService.mapGetAccountInfoToUserInfo(response);

      assertThat(result.userId()).isNull();
      assertThat(result.fullName()).isEmpty();
      assertThat(result.email()).isEqualTo("user@example.com");
      assertThat(result.domain()).isNull();
      assertThat(result.status()).isEqualTo("ACTIVE");
      assertThat(result.type()).isEqualTo("INTERNAL");
    }
  }
}
