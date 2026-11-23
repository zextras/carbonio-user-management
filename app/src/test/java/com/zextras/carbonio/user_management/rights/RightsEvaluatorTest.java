package com.zextras.carbonio.user_management.rights;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.benmanes.caffeine.cache.Ticker;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.Modification;
import com.unboundid.ldap.sdk.ModificationType;
import com.zextras.carbonio.user_management.ldap.LdapDirectory;
import com.zextras.carbonio.user_management.rights.RightsEvaluator.Target;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RightsEvaluatorTest {

  private static final String BASE = "dc=root";
  private static final String D1 = "d1000000-0000-0000-0000-000000000001";
  private static final String D2 = "d2000000-0000-0000-0000-000000000002";
  private static final String SUB = "d3000000-0000-0000-0000-000000000003";
  private static final String GLOBAL_ADMIN = "a0000000-0000-0000-0000-00000000000g";
  private static final String USERS_ADMIN = "a0000000-0000-0000-0000-00000000000a";
  private static final String QUOTA_ADMIN = "a0000000-0000-0000-0000-00000000000b";
  private static final String NOT_DELEGATED = "a0000000-0000-0000-0000-00000000000n";
  private static final String USER = "u0000000-0000-0000-0000-000000000001";
  private static final String OTHER_DOMAIN_USER = "u0000000-0000-0000-0000-000000000002";
  private static final String TARGET_ADMIN = "u0000000-0000-0000-0000-000000000003";
  private static final String USERS_ADMINS_DL = "g0000000-0000-0000-0000-000000000001";
  private static final String DELEGATED_ADMINS_DL = "g0000000-0000-0000-0000-000000000002";
  private static final String COS = "c0000000-0000-0000-0000-000000000001";

  private static RightsCatalog catalog;
  private InMemoryDirectoryServer server;
  private LdapDirectory directory;
  private RightsEvaluator evaluator;

  @BeforeAll
  static void loadCatalog() {
    catalog = new RightsCatalog();
  }

  @BeforeEach
  void setUp() throws Exception {
    InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(BASE, "cn=zimbra");
    config.setSchema(null);
    server = new InMemoryDirectoryServer(config);
    server.startListening();
    server.add("dn: " + BASE, "objectClass: top", "objectClass: domain", "dc: root");
    server.add(
        "dn: cn=zimbra", "objectClass: top", "objectClass: organizationalRole", "cn: zimbra");
    server.add("dn: cn=config,cn=zimbra", "objectClass: zimbraGlobalConfig", "cn: config");
    server.add("dn: cn=globalgrant,cn=zimbra", "objectClass: zimbraAclTarget", "cn: globalgrant");

    domain("d1.io", D1);
    domain("d2.io", D2);
    domain("sub.d1.io", SUB);
    server.add("dn: cn=cos1," + BASE, "objectClass: zimbraCOS", "cn: cos1", "zimbraId: " + COS);

    account(GLOBAL_ADMIN, "global@d1.io", "zimbraIsAdminAccount: TRUE");
    account(USERS_ADMIN, "usersadmin@d1.io", "zimbraIsDelegatedAdminAccount: TRUE");
    account(QUOTA_ADMIN, "quotaadmin@d1.io", "zimbraIsDelegatedAdminAccount: TRUE");
    account(NOT_DELEGATED, "plain@d1.io");
    account(USER, "user@d1.io");
    account(OTHER_DOMAIN_USER, "user@d2.io");
    account(TARGET_ADMIN, "admin2@d1.io", "zimbraIsAdminAccount: TRUE");

    distributionList(
        USERS_ADMINS_DL,
        "__users_admins@d1.io",
        "usersadmin@d1.io",
        "plain@d1.io",
        "__delegated_admins@d1.io");
    distributionList(DELEGATED_ADMINS_DL, "__delegated_admins@d1.io", "quotaadmin@d1.io");

    addAces(
        domainDn("d1.io"),
        USERS_ADMINS_DL + " grp domainAdminAccountRights",
        DELEGATED_ADMINS_DL + " grp setAccountQuota");

    rebuild();
  }

  private void rebuild() throws Exception {
    directory = new LdapDirectory(server.getConnectionPool(2), BASE, Ticker.systemTicker());
    evaluator = new RightsEvaluator(directory, catalog);
  }

  private void modify(String dn, Modification modification) throws Exception {
    server.modify(dn, modification);
    rebuild();
  }

  @AfterEach
  void tearDown() {
    server.shutDown(true);
  }

  @Nested
  class HardRules {

    @Test
    void globalAdminIsAllowedEverything() {
      assertThat(check(GLOBAL_ADMIN, "modifyAccount", account(USER))).isTrue();
      assertThat(check(GLOBAL_ADMIN, "setAccountQuota", account(OTHER_DOMAIN_USER))).isTrue();
    }

    @Test
    void globalAdminWithoutAdminSessionIsDenied() {
      Entry actor = entry(GLOBAL_ADMIN);
      assertThat(evaluator.check(actor, false, right("modifyAccount"), account(USER))).isFalse();
    }

    @Test
    void accountWithoutDelegatedFlagIsDeniedEvenWithGroupGrants() {
      assertThat(
              check(NOT_DELEGATED, "setDomainAdminAccountAndCalendarResourceAttrs", account(USER)))
          .isFalse();
    }

    @Test
    void delegatedAdminCannotActOnGlobalAdmin() {
      assertThat(
              check(
                  USERS_ADMIN,
                  "setDomainAdminAccountAndCalendarResourceAttrs",
                  account(TARGET_ADMIN)))
          .isFalse();
    }

    @Test
    void suspendedDomainDeniesEvenGlobalAdmin() throws Exception {
      modify(
          domainDn("d1.io"),
          new Modification(ModificationType.REPLACE, "zimbraDomainStatus", "suspended"));
      assertThat(check(GLOBAL_ADMIN, "modifyAccount", account(USER))).isFalse();
    }

    @Test
    void userRightsAreRejected() {
      assertThatThrownBy(() -> check(USERS_ADMIN, "sendAs", account(USER)))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class AdvancedProfiles {

    @Test
    void usersAdminCanConfigureButNotModifyEveryAttribute() {
      assertThat(check(USERS_ADMIN, "modifyAccount", account(USER))).isFalse();
      assertThat(check(USERS_ADMIN, "setDomainAdminAccountAndCalendarResourceAttrs", account(USER)))
          .isTrue();
      assertThat(check(USERS_ADMIN, "set.account.zimbraMailQuota", account(USER))).isTrue();
      assertThat(check(USERS_ADMIN, "getAccount", account(USER))).isTrue();
      assertThat(check(USERS_ADMIN, "getMailboxInfo", account(USER))).isTrue();
    }

    @Test
    void usersAdminLacksTheQuotaPresetRight() {
      assertThat(check(USERS_ADMIN, "setAccountQuota", account(USER))).isFalse();
    }

    @Test
    void nestedGroupMembershipInheritsGrants() {
      assertThat(check(QUOTA_ADMIN, "setAccountQuota", account(USER))).isTrue();
      assertThat(check(QUOTA_ADMIN, "setDomainAdminAccountAndCalendarResourceAttrs", account(USER)))
          .isTrue();
    }

    @Test
    void grantsOnOneDomainDoNotReachAnother() {
      assertThat(
              check(
                  USERS_ADMIN,
                  "setDomainAdminAccountAndCalendarResourceAttrs",
                  account(OTHER_DOMAIN_USER)))
          .isFalse();
    }

    @Test
    void grantedComboIsSatisfied() {
      assertThat(check(USERS_ADMIN, "domainAdminAccountRights", account(USER))).isTrue();
      assertThat(check(USERS_ADMIN, "domainAdminRights", account(USER))).isFalse();
    }

    @Test
    void nonAdminGroupsDoNotGrantAdminRights() throws Exception {
      modify(
          "cn=__users_admins," + BASE,
          new Modification(ModificationType.REPLACE, "zimbraIsAdminGroup", "FALSE"));
      assertThat(check(USERS_ADMIN, "getAccount", account(USER))).isFalse();
    }
  }

  @Nested
  class Precedence {

    @Test
    void denyOnTheAccountBeatsAllowOnTheDomain() throws Exception {
      addAces(accountDn("user@d1.io"), USERS_ADMIN + " usr -getMailboxInfo");
      assertThat(check(USERS_ADMIN, "getMailboxInfo", account(USER))).isFalse();
    }

    @Test
    void allowOnTheAccountBeatsDenyOnTheDomain() throws Exception {
      addAces(domainDn("d1.io"), QUOTA_ADMIN + " usr -getMailboxInfo");
      addAces(accountDn("user@d1.io"), QUOTA_ADMIN + " usr getMailboxInfo");
      assertThat(check(QUOTA_ADMIN, "getMailboxInfo", account(USER))).isTrue();
    }

    @Test
    void userGranteeBeatsGroupGranteeOnTheSameTarget() throws Exception {
      addAces(domainDn("d1.io"), USERS_ADMIN + " usr -getMailboxInfo");
      assertThat(check(USERS_ADMIN, "getMailboxInfo", account(USER))).isFalse();
      addAces(domainDn("d1.io"), USERS_ADMINS_DL + " grp -getAccountInfo");
      addAces(domainDn("d1.io"), USERS_ADMIN + " usr getAccountInfo");
      assertThat(check(USERS_ADMIN, "getAccountInfo", account(USER))).isTrue();
    }

    @Test
    void attributeDenyCloserThanTheAllowRemovesOnlyThatAttribute() throws Exception {
      addAces(domainDn("d1.io"), USERS_ADMIN + " usr -set.account.displayName");
      assertThat(check(USERS_ADMIN, "set.account.displayName", account(USER))).isFalse();
      assertThat(check(USERS_ADMIN, "set.account.zimbraMailQuota", account(USER))).isTrue();
      assertThat(check(USERS_ADMIN, "setDomainAdminAccountAndCalendarResourceAttrs", account(USER)))
          .isFalse();
    }

    @Test
    void attributeAllowCloserThanTheDenyWins() throws Exception {
      addAces(domainDn("d1.io"), USERS_ADMINS_DL + " grp -set.account.displayName");
      addAces(accountDn("user@d1.io"), USERS_ADMIN + " usr set.account.displayName");
      assertThat(check(USERS_ADMIN, "set.account.displayName", account(USER))).isTrue();
    }

    @Test
    void setGrantImpliesGetButNotTheOtherWayRound() throws Exception {
      addAces(accountDn("user@d2.io"), USERS_ADMIN + " usr set.account.zimbraNotes");
      addAces(accountDn("user@d2.io"), USERS_ADMIN + " usr get.account.displayName");
      assertThat(check(USERS_ADMIN, "get.account.zimbraNotes", account(OTHER_DOMAIN_USER)))
          .isTrue();
      assertThat(check(USERS_ADMIN, "set.account.displayName", account(OTHER_DOMAIN_USER)))
          .isFalse();
    }

    @Test
    void allAttributesDenyBlocksLessSpecificAllows() throws Exception {
      addAces(accountDn("user@d1.io"), USERS_ADMIN + " usr -modifyAccount");
      assertThat(check(USERS_ADMIN, "set.account.zimbraMailQuota", account(USER))).isFalse();
    }
  }

  @Nested
  class TargetGroups {

    private static final String ATTR_RIGHT = "set.account.zimbraMailHost";

    @Test
    void grantOnAGroupOfTheTargetApplies() throws Exception {
      distributionList("g0000000-0000-0000-0000-0000000000t1", "team@d1.io", "user@d1.io");
      assertThat(check(QUOTA_ADMIN, ATTR_RIGHT, account(USER))).isFalse();
      addAces("cn=team," + BASE, QUOTA_ADMIN + " usr " + ATTR_RIGHT);
      assertThat(check(QUOTA_ADMIN, ATTR_RIGHT, account(USER))).isTrue();
    }

    @Test
    void disinheritedGrantAppliesToDirectMembersOnly() throws Exception {
      distributionList("g0000000-0000-0000-0000-0000000000t1", "team@d1.io", "user@d1.io");
      distributionList("g0000000-0000-0000-0000-0000000000t2", "dept@d1.io", "team@d1.io");
      addAces("cn=dept," + BASE, QUOTA_ADMIN + " usr ^" + ATTR_RIGHT);
      assertThat(check(QUOTA_ADMIN, ATTR_RIGHT, account(USER))).isFalse();
      addAces("cn=team," + BASE, QUOTA_ADMIN + " usr ^" + ATTR_RIGHT);
      assertThat(check(QUOTA_ADMIN, ATTR_RIGHT, account(USER))).isTrue();
    }

    @Test
    void groupInAThirdDomainNeedsCrossDomainAdminOnTheTargetDomain() throws Exception {
      distributionList("g0000000-0000-0000-0000-0000000000t3", "partners@d1.io", "user@d2.io");
      addAces("cn=partners," + BASE, QUOTA_ADMIN + " usr " + ATTR_RIGHT);
      account(
          "a0000000-0000-0000-0000-00000000000x",
          "x@sub.d1.io",
          "zimbraIsDelegatedAdminAccount: TRUE");
      addAces("cn=partners," + BASE, "a0000000-0000-0000-0000-00000000000x usr " + ATTR_RIGHT);

      assertThat(check(QUOTA_ADMIN, ATTR_RIGHT, account(OTHER_DOMAIN_USER))).isFalse();
      assertThat(
              check("a0000000-0000-0000-0000-00000000000x", ATTR_RIGHT, account(OTHER_DOMAIN_USER)))
          .isFalse();

      addAces(domainDn("d2.io"), D1 + " dom crossDomainAdmin");
      assertThat(check(QUOTA_ADMIN, ATTR_RIGHT, account(OTHER_DOMAIN_USER))).isTrue();
      assertThat(
              check("a0000000-0000-0000-0000-00000000000x", ATTR_RIGHT, account(OTHER_DOMAIN_USER)))
          .isFalse();
    }

    @Test
    void groupInTheTargetDomainNeedsNoCrossDomainAdmin() throws Exception {
      distributionList("g0000000-0000-0000-0000-0000000000t4", "staff@d2.io", "user@d2.io");
      addAces("cn=staff," + BASE, QUOTA_ADMIN + " usr " + ATTR_RIGHT);
      assertThat(check(QUOTA_ADMIN, ATTR_RIGHT, account(OTHER_DOMAIN_USER))).isTrue();
    }

    @Test
    void denyOnACrossDomainGroupStillApplies() throws Exception {
      distributionList("g0000000-0000-0000-0000-0000000000t3", "partners@d1.io", "user@d2.io");
      addAces("cn=partners," + BASE, QUOTA_ADMIN + " usr -getAccountInfo");
      addAces(domainDn("d2.io"), QUOTA_ADMIN + " usr getAccountInfo");
      assertThat(check(QUOTA_ADMIN, "getAccountInfo", account(OTHER_DOMAIN_USER))).isFalse();
    }
  }

  @Nested
  class Freshness {

    @Test
    void grantChangesOnCachedEntriesAreSeenImmediately() throws Exception {
      directory.domainByName("d1.io");
      directory.globalGrant(LdapDirectory.Read.CACHED);
      assertThat(check(QUOTA_ADMIN, "getDomainQuotaUsage", domain("d1.io"))).isFalse();

      server.modify(
          domainDn("d1.io"),
          new Modification(
              ModificationType.ADD, "zimbraACE", QUOTA_ADMIN + " usr getDomainQuotaUsage"));
      server.modify(
          "cn=globalgrant,cn=zimbra",
          new Modification(ModificationType.ADD, "zimbraACE", QUOTA_ADMIN + " usr getAccountInfo"));

      assertThat(check(QUOTA_ADMIN, "getAccountInfo", account(OTHER_DOMAIN_USER))).isTrue();
      Target freshDomain =
          new Target(
              "domain", directory.domainByName("d1.io", LdapDirectory.Read.FRESH).orElseThrow());
      assertThat(check(QUOTA_ADMIN, "getDomainQuotaUsage", freshDomain)).isTrue();
    }
  }

  @Nested
  class OtherTargets {

    @Test
    void domainGrantAppliesToTheDomainItself() throws Exception {
      addAces(domainDn("d1.io"), QUOTA_ADMIN + " usr getDomain");
      assertThat(check(QUOTA_ADMIN, "getDomain", domain("d1.io"))).isTrue();
      assertThat(check(QUOTA_ADMIN, "getDomain", domain("sub.d1.io"))).isFalse();
    }

    @Test
    void subDomainGrantAppliesOnlyToSubDomains() throws Exception {
      addAces(domainDn("d1.io"), QUOTA_ADMIN + " usr *getDomain");
      assertThat(check(QUOTA_ADMIN, "getDomain", domain("d1.io"))).isFalse();
      assertThat(check(QUOTA_ADMIN, "getDomain", domain("sub.d1.io"))).isTrue();
    }

    @Test
    void cosGrant() throws Exception {
      assertThat(check(QUOTA_ADMIN, "configureQuota", cos())).isFalse();
      addAces("cn=cos1," + BASE, QUOTA_ADMIN + " usr configureQuota");
      assertThat(check(QUOTA_ADMIN, "configureQuota", cos())).isTrue();
    }

    @Test
    void globalGrantReachesEveryAccount() throws Exception {
      addAces("cn=globalgrant,cn=zimbra", QUOTA_ADMIN + " usr getAccountInfo");
      assertThat(check(QUOTA_ADMIN, "getAccountInfo", account(OTHER_DOMAIN_USER))).isTrue();
    }
  }

  private boolean check(String actorId, String rightName, Target target) {
    return evaluator.check(entry(actorId), true, right(rightName), target);
  }

  private Right right(String name) {
    return catalog.find(name).orElseThrow();
  }

  private Entry entry(String id) {
    return directory.accountById(id).orElseThrow();
  }

  private Target account(String id) {
    return new Target("account", entry(id));
  }

  private Target domain(String name) {
    return new Target("domain", directory.domainByName(name).orElseThrow());
  }

  private Target cos() {
    return new Target("cos", directory.cosById(COS).orElseThrow());
  }

  private void domain(String name, String id) throws Exception {
    server.add(
        "dn: " + domainDn(name),
        "objectClass: zimbraDomain",
        "zimbraDomainName: " + name,
        "zimbraId: " + id,
        "zimbraDomainStatus: active");
  }

  private void account(String id, String name, String... extra) throws Exception {
    String[] lines = new String[5 + extra.length];
    lines[0] = "dn: " + accountDn(name);
    lines[1] = "objectClass: zimbraAccount";
    lines[2] = "zimbraId: " + id;
    lines[3] = "zimbraMailDeliveryAddress: " + name;
    lines[4] = "uid: " + name.replace('@', '_');
    System.arraycopy(extra, 0, lines, 5, extra.length);
    server.add(lines);
  }

  private void distributionList(String id, String name, String... members) throws Exception {
    String cn = name.substring(0, name.indexOf('@'));
    String[] lines = new String[6 + members.length];
    lines[0] = "dn: cn=" + cn + "," + BASE;
    lines[1] = "objectClass: zimbraDistributionList";
    lines[2] = "cn: " + cn;
    lines[3] = "zimbraId: " + id;
    lines[4] = "mail: " + name;
    lines[5] = "zimbraIsAdminGroup: TRUE";
    for (int i = 0; i < members.length; i++) {
      lines[6 + i] = "zimbraMailForwardingAddress: " + members[i];
    }
    server.add(lines);
  }

  private void addAces(String dn, String... aces) throws Exception {
    modify(dn, new Modification(ModificationType.ADD, "zimbraACE", aces));
  }

  private static String domainDn(String name) {
    return "zimbraDomainName=" + name + "," + BASE;
  }

  private static String accountDn(String name) {
    return "uid=" + name.replace('@', '_') + "," + BASE;
  }
}
