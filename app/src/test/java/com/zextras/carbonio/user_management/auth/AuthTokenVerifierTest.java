package com.zextras.carbonio.user_management.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Ticker;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.sdk.Modification;
import com.unboundid.ldap.sdk.ModificationType;
import com.zextras.carbonio.user_management.ldap.LdapDirectory;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthTokenVerifierTest {

  private static final String BASE = "dc=root";
  private static final String ACCOUNT_ID = "11111111-2222-3333-4444-555555555555";
  private static final String KEY_HEX =
      "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff";
  private static final String OTHER_KEY_HEX =
      "ffeeddccbbaa99887766554433221100ffeeddccbbaa99887766554433221100";
  private static final long NOW = 1_800_000_000_000L;

  private InMemoryDirectoryServer server;
  private AuthTokenVerifier verifier;

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
        "zimbraAuthTokenKey: 0:1600000000000:" + KEY_HEX);
    server.add(
        "dn: uid=user," + BASE,
        "objectClass: zimbraAccount",
        "uid: user",
        "zimbraId: " + ACCOUNT_ID,
        "zimbraMailDeliveryAddress: user@example.com",
        "zimbraAuthTokens: 12345|" + (NOW + 3_600_000) + "|carbonio",
        "zimbraAuthTokenValidityValue: 2");
    LdapDirectory directory =
        new LdapDirectory(server.getConnectionPool(1), BASE, Ticker.systemTicker());
    verifier =
        new AuthTokenVerifier(directory, Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC));
  }

  @AfterEach
  void tearDown() {
    server.shutDown(true);
  }

  @Test
  void acceptsAValidRegisteredToken() {
    var verified = verifier.verify(token("0", KEY_HEX, claims()));

    assertThat(verified).isPresent();
    assertThat(verified.get().account().getAttributeValue("zimbraId")).isEqualTo(ACCOUNT_ID);
    assertThat(verified.get().expiresAt()).isEqualTo(NOW + 60_000);
    assertThat(verified.get().anyAdmin()).isFalse();
  }

  @Test
  void reportsAdminTokens() {
    Map<String, String> claims = claims();
    claims.put("admin", "1");

    assertThat(verifier.verify(token("0", KEY_HEX, claims)).orElseThrow().anyAdmin()).isTrue();
  }

  @Test
  void rejectsATamperedSignature() {
    String valid = token("0", KEY_HEX, claims());
    String[] parts = valid.split("_", 3);
    Map<String, String> forged = claims();
    forged.put("id", "99999999-2222-3333-4444-555555555555");

    assertThat(verifier.verify(parts[0] + "_" + parts[1] + "_" + hexData(forged))).isEmpty();
    assertThat(verifier.verify(token("0", OTHER_KEY_HEX, claims()))).isEmpty();
  }

  @Test
  void rejectsMalformedTokens() {
    assertThat(verifier.verify("")).isEmpty();
    assertThat(verifier.verify("0_abc")).isEmpty();
    assertThat(verifier.verify("0_abc_zz")).isEmpty();
    assertThat(verifier.verify("x_" + "a".repeat(40) + "_00")).isEmpty();
  }

  @Test
  void rejectsAnExpiredToken() {
    Map<String, String> claims = claims();
    claims.put("exp", Long.toString(NOW - 1));

    assertThat(verifier.verify(token("0", KEY_HEX, claims))).isEmpty();
  }

  @Test
  void rejectsAnUnregisteredToken() {
    Map<String, String> claims = claims();
    claims.put("tid", "54321");

    assertThat(verifier.verify(token("0", KEY_HEX, claims))).isEmpty();
  }

  @Test
  void externalTokensSkipRegistration() {
    Map<String, String> claims = claims();
    claims.put("tid", "54321");
    claims.put("type", "external");

    assertThat(verifier.verify(token("0", KEY_HEX, claims))).isPresent();
  }

  @Test
  void rejectsATokenOfAnOlderValidityValue() {
    Map<String, String> claims = claims();
    claims.put("vv", "1");

    assertThat(verifier.verify(token("0", KEY_HEX, claims))).isEmpty();
  }

  @Test
  void ignoresTheValidityValueWhenDisabled() throws Exception {
    server.modify(
        "cn=config,cn=zimbra",
        new Modification(ModificationType.REPLACE, "zimbraAuthTokenValidityValueEnabled", "FALSE"));
    Map<String, String> claims = claims();
    claims.put("vv", "1");

    assertThat(verifier.verify(token("0", KEY_HEX, claims))).isPresent();
  }

  @Test
  void rejectsTokensOfDeletedAccounts() throws Exception {
    server.delete("uid=user," + BASE);

    assertThat(verifier.verify(token("0", KEY_HEX, claims()))).isEmpty();
  }

  @Test
  void picksUpARotatedKeyOnce() throws Exception {
    server.modify(
        "cn=config,cn=zimbra",
        new Modification(
            ModificationType.ADD, "zimbraAuthTokenKey", "1:1700000000000:" + OTHER_KEY_HEX));

    assertThat(verifier.verify(token("1", OTHER_KEY_HEX, claims()))).isPresent();
    assertThat(verifier.verify(token("0", KEY_HEX, claims()))).isPresent();
  }

  private static Map<String, String> claims() {
    Map<String, String> claims = new LinkedHashMap<>();
    claims.put("id", ACCOUNT_ID);
    claims.put("exp", Long.toString(NOW + 60_000));
    claims.put("type", "zimbra");
    claims.put("tid", "12345");
    claims.put("vv", "2");
    claims.put("version", "carbonio");
    return claims;
  }

  static String token(String version, String keyHex, Map<String, String> claims) {
    String data = hexData(claims);
    return version
        + "_"
        + AuthTokenVerifier.hmacHex(data, HexFormat.of().parseHex(keyHex))
        + "_"
        + data;
  }

  private static String hexData(Map<String, String> claims) {
    StringBuilder encoded = new StringBuilder();
    claims.forEach(
        (name, value) ->
            encoded
                .append(name)
                .append('=')
                .append(value.length())
                .append(':')
                .append(value)
                .append(';'));
    return HexFormat.of().formatHex(encoded.toString().getBytes(StandardCharsets.UTF_8));
  }
}
