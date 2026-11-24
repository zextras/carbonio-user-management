package com.zextras.carbonio.user_management.ldap;

import static org.assertj.core.api.Assertions.assertThat;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.sdk.Modification;
import com.unboundid.ldap.sdk.ModificationType;
import com.zextras.carbonio.user_management.auth.AuthTokenVerifier;
import com.zextras.carbonio.user_management.record.UserInfo;
import com.zextras.carbonio.user_management.record.UserMyself;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LdapUserDirectoryTest {

  private static final String BASE = "dc=root";
  private static final String DOMAIN_ID = "d0000000-0000-0000-0000-000000000001";
  private static final String ALIAS_DOMAIN_ID = "d0000000-0000-0000-0000-000000000002";
  private static final String DEFAULT_COS_ID = "c0000000-0000-0000-0000-000000000000";
  private static final String DOMAIN_COS_ID = "c0000000-0000-0000-0000-000000000001";
  private static final String EXPLICIT_COS_ID = "c0000000-0000-0000-0000-000000000002";
  private static final String EXTERNAL_COS_ID = "c0000000-0000-0000-0000-000000000003";
  private static final String USER_ID = "a0000000-0000-0000-0000-000000000001";
  private static final String EXPLICIT_COS_USER_ID = "a0000000-0000-0000-0000-000000000002";
  private static final String GUEST_ID = "a0000000-0000-0000-0000-000000000003";

  private InMemoryDirectoryServer server;
  private LdapUserDirectory directory;

  @BeforeEach
  void setUp() throws Exception {
    InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(BASE, "cn=zimbra");
    config.setSchema(null);
    server = new InMemoryDirectoryServer(config);
    server.startListening();
    server.add("dn: " + BASE, "objectClass: domain", "dc: root");
    server.add("dn: cn=zimbra", "objectClass: organizationalRole", "cn: zimbra");
    server.add(
        "dn: cn=config,cn=zimbra",
        "objectClass: zimbraGlobalConfig",
        "cn: config",
        "zimbraDefaultDomainName: example.com",
        "zimbraLocale: it");
    server.add(
        "dn: cn=mbox1.example.com," + BASE,
        "objectClass: zimbraServer",
        "cn: mbox1.example.com",
        "zimbraLocale: fr");
    server.add(
        "dn: zimbraDomainName=example.com," + BASE,
        "objectClass: zimbraDomain",
        "zimbraDomainName: example.com",
        "zimbraId: " + DOMAIN_ID,
        "zimbraDomainDefaultCOSId: " + DOMAIN_COS_ID,
        "zimbraDomainDefaultExternalUserCOSId: " + EXTERNAL_COS_ID,
        "zimbraPublicServiceHostname: mail.example.com",
        "zimbraPublicServiceProtocol: https");
    server.add(
        "dn: zimbraDomainName=alias.example," + BASE,
        "objectClass: zimbraDomain",
        "zimbraDomainName: alias.example",
        "zimbraId: " + ALIAS_DOMAIN_ID,
        "zimbraDomainType: alias",
        "zimbraDomainAliasTargetId: " + DOMAIN_ID);
    cos("default", DEFAULT_COS_ID, "carbonioFeatureChatsEnabled: TRUE");
    cos(
        "domaincos",
        DOMAIN_COS_ID,
        "carbonioFeatureFilesEnabled: TRUE",
        "carbonioFeatureTasksEnabled: TRUE",
        "carbonioFeatureMailsAppEnabled: FALSE",
        "carbonioWscMaxGroupMembers: 128",
        "zimbraPrefLocale: de");
    cos("explicit", EXPLICIT_COS_ID, "carbonioFeatureFilesEnabled: FALSE");
    cos("defaultExternal", EXTERNAL_COS_ID, "carbonioFeatureFilesEnabled: TRUE");
    server.add(
        "dn: uid=user," + BASE,
        "objectClass: zimbraAccount",
        "uid: user",
        "zimbraId: " + USER_ID,
        "zimbraMailDeliveryAddress: user@example.com",
        "zimbraMailAlias: alias@example.com",
        "zimbraOldMailAddress: old@example.com",
        "displayName: User One",
        "zimbraAccountStatus: locked",
        "zimbraMailHost: mbox1.example.com",
        "carbonioFeatureTasksEnabled: FALSE",
        "carbonioWscMaxGroupMembers: 64",
        "zimbraIsAdminAccount: TRUE");
    server.add(
        "dn: uid=explicit," + BASE,
        "objectClass: zimbraAccount",
        "uid: explicit",
        "zimbraId: " + EXPLICIT_COS_USER_ID,
        "zimbraMailDeliveryAddress: explicit@example.com",
        "zimbraCOSId: " + EXPLICIT_COS_ID,
        "zimbraPrefLocale: en-us");
    server.add(
        "dn: uid=guest," + BASE,
        "objectClass: zimbraAccount",
        "uid: guest",
        "zimbraId: " + GUEST_ID,
        "zimbraMailDeliveryAddress: guest@example.com",
        "zimbraIsExternalVirtualAccount: TRUE");

    LdapDirectory ldap =
        new LdapDirectory(server.getConnectionPool(2), BASE);
    directory =
        new LdapUserDirectory(
            ldap,
            new AccountResolver(ldap),
            new AuthTokenVerifier(ldap, Clock.systemUTC()),
            Clock.systemUTC());
  }

  @AfterEach
  void tearDown() {
    server.shutDown(true);
  }

  @Nested
  class UserInfoMapping {

    @Test
    void mapsTheAccount() {
      UserInfo info = directory.byId(USER_ID).orElseThrow();

      assertThat(info)
          .isEqualTo(
              new UserInfo(
                  USER_ID,
                  "user@example.com",
                  "User One",
                  "https://mail.example.com",
                  "LOCKED",
                  "INTERNAL",
                  DOMAIN_COS_ID,
                  DOMAIN_ID,
                  true));
    }

    @Test
    void explicitCosWinsOverTheDomainDefault() {
      UserInfo info = directory.byId(EXPLICIT_COS_USER_ID).orElseThrow();

      assertThat(info.cosId()).isEqualTo(EXPLICIT_COS_ID);
      assertThat(info.fullName()).isEmpty();
      assertThat(info.status()).isEqualTo("ACTIVE");
      assertThat(info.isGlobalAdmin()).isFalse();
    }

    @Test
    void danglingExplicitCosIsReportedAsIs() throws Exception {
      server.modify(
          "uid=explicit," + BASE,
          new Modification(ModificationType.REPLACE, "zimbraCOSId", "missing-cos"));

      assertThat(directory.byId(EXPLICIT_COS_USER_ID).orElseThrow().cosId())
          .isEqualTo("missing-cos");
    }

    @Test
    void fallsBackToTheDefaultCosWhenTheDomainHasNone() throws Exception {
      server.modify(
          "zimbraDomainName=example.com," + BASE,
          new Modification(ModificationType.DELETE, "zimbraDomainDefaultCOSId"));

      assertThat(directory.byId(USER_ID).orElseThrow().cosId()).isEqualTo(DEFAULT_COS_ID);
    }

    @Test
    void guestsUseTheExternalCos() {
      UserInfo guest = directory.byId(GUEST_ID).orElseThrow();

      assertThat(guest.type()).isEqualTo("GUEST");
      assertThat(guest.cosId()).isEqualTo(EXTERNAL_COS_ID);
    }

    @Test
    void domainNameIsUsedWithoutAPublicHostname() throws Exception {
      server.modify(
          "zimbraDomainName=example.com," + BASE,
          new Modification(ModificationType.DELETE, "zimbraPublicServiceHostname"));

      assertThat(directory.byId(USER_ID).orElseThrow().domain()).isEqualTo("example.com");
    }

    @Test
    void unknownIdIsEmpty() {
      assertThat(directory.byId("nope")).isEmpty();
    }
  }

  @Nested
  class LookupByEmail {

    @Test
    void findsByPrimaryAliasAndOldAddressIgnoringCase() {
      assertThat(directory.byEmail("user@example.com"))
          .get()
          .extracting(UserInfo::userId)
          .isEqualTo(USER_ID);
      assertThat(directory.byEmail("ALIAS@example.com"))
          .get()
          .extracting(UserInfo::userId)
          .isEqualTo(USER_ID);
      assertThat(directory.byEmail("old@example.com"))
          .get()
          .extracting(UserInfo::userId)
          .isEqualTo(USER_ID);
    }

    @Test
    void findsThroughAnAliasDomain() {
      assertThat(directory.byEmail("user@alias.example"))
          .get()
          .extracting(UserInfo::userId)
          .isEqualTo(USER_ID);
    }

    @Test
    void addsTheDefaultDomainToALocalPart() {
      assertThat(directory.byEmail("user")).get().extracting(UserInfo::userId).isEqualTo(USER_ID);
    }

    @Test
    void unknownAddressIsEmpty() {
      assertThat(directory.byEmail("nobody@example.com")).isEmpty();
    }
  }

  @Test
  void batchReturnsOnlyExistingAccounts() {
    List<UserInfo> users = directory.byIds(List.of(USER_ID, "nope", GUEST_ID));

    assertThat(users).extracting(UserInfo::userId).containsExactlyInAnyOrder(USER_ID, GUEST_ID);
  }

  @Nested
  class Myself {

    private UserMyself myself(String id) throws Exception {
      return UserMapper.toUserMyself(
          new AccountResolver(ldap()).resolve(ldap().accountById(id).orElseThrow()),
          ldap()::serverByName);
    }

    private LdapDirectory ldap() throws Exception {
      return new LdapDirectory(server.getConnectionPool(1), BASE);
    }

    @Test
    void featuresAreTheEnabledOnesWithAccountOverridingCos() throws Exception {
      assertThat(myself(USER_ID).features()).containsExactly("carbonioFeatureFilesEnabled");
    }

    @Test
    void capabilitiesKeepRawValuesWithAccountOverridingCos() throws Exception {
      assertThat(myself(USER_ID).capabilities())
          .containsEntry("carbonioWscMaxGroupMembers", "64")
          .doesNotContainKey("carbonioFeatureFilesEnabled");
    }

    @Test
    void localeFollowsTheMailboxChain() throws Exception {
      assertThat(myself(USER_ID).locale()).isEqualTo("de");
      assertThat(myself(EXPLICIT_COS_USER_ID).locale()).isEqualTo("en_US");
      assertThat(myself(GUEST_ID).locale()).isEqualTo("it");

      server.modify(
          "cn=domaincos," + BASE, new Modification(ModificationType.DELETE, "zimbraPrefLocale"));
      assertThat(myself(USER_ID).locale()).isEqualTo("fr");
    }
  }

  private void cos(String name, String id, String... attributes) throws Exception {
    String[] lines = new String[4 + attributes.length];
    lines[0] = "dn: cn=" + name + "," + BASE;
    lines[1] = "objectClass: zimbraCOS";
    lines[2] = "cn: " + name;
    lines[3] = "zimbraId: " + id;
    System.arraycopy(attributes, 0, lines, 4, attributes.length);
    server.add(lines);
  }
}
