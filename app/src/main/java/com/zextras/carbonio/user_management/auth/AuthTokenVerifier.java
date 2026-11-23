package com.zextras.carbonio.user_management.auth;

import com.unboundid.ldap.sdk.Entry;
import com.zextras.carbonio.user_management.UserManagementServiceConfig.ZimbraAttributes;
import com.zextras.carbonio.user_management.ldap.LdapDirectory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Mailbox /internal/accounts/myself checks plus the SOAP validity-value check.
@ApplicationScoped
public class AuthTokenVerifier {

  private static final Logger logger = LoggerFactory.getLogger(AuthTokenVerifier.class);
  static final Duration KEY_RELOAD_INTERVAL = Duration.ofSeconds(10);
  private static final String HMAC_ALGORITHM = "HmacSHA1";

  private final LdapDirectory directory;
  private final Clock clock;
  private final AtomicLong lastKeyReload = new AtomicLong(Long.MIN_VALUE);

  @Inject
  public AuthTokenVerifier(LdapDirectory directory, Clock clock) {
    this.directory = directory;
    this.clock = clock;
  }

  public Optional<VerifiedToken> verify(String encoded) {
    Optional<AuthTokenClaims> claims = verifySignature(encoded);
    if (claims.isEmpty()) {
      return Optional.empty();
    }
    AuthTokenClaims token = claims.get();
    if (token.accountId() == null || token.expiresAt() == null) {
      return Optional.empty();
    }
    if (clock.millis() > token.expiresAt()) {
      logger.debug("Auth token expired");
      return Optional.empty();
    }
    Optional<Entry> account = directory.accountById(token.accountId());
    if (account.isEmpty()) {
      return Optional.empty();
    }
    if (token.isZimbraUser() && !isRegistered(account.get(), token.tokenId())) {
      logger.debug("Auth token not registered on account {}", token.accountId());
      return Optional.empty();
    }
    if (!validityValueMatches(account.get(), token.validityValue())) {
      logger.debug("Auth token validity value mismatch on account {}", token.accountId());
      return Optional.empty();
    }
    return Optional.of(new VerifiedToken(account.get(), token.expiresAt(), token.anyAdmin()));
  }

  Optional<AuthTokenClaims> verifySignature(String encoded) {
    int first = encoded.indexOf('_');
    int second = first < 0 ? -1 : encoded.indexOf('_', first + 1);
    if (second < 0) {
      return Optional.empty();
    }
    String version = encoded.substring(0, first);
    String hmac = encoded.substring(first + 1, second);
    String data = encoded.substring(second + 1);

    Optional<byte[]> key = key(version);
    if (key.isEmpty()) {
      logger.debug("Unknown auth token key version {}", version);
      return Optional.empty();
    }
    byte[] expected = hmacHex(data, key.get()).getBytes(StandardCharsets.US_ASCII);
    if (!MessageDigest.isEqual(expected, hmac.getBytes(StandardCharsets.US_ASCII))) {
      return Optional.empty();
    }
    try {
      String decoded = new String(HexFormat.of().parseHex(data), StandardCharsets.UTF_8);
      return Optional.of(AuthTokenClaims.from(BlobMetaData.decode(decoded)));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private Optional<byte[]> key(String version) {
    Optional<byte[]> key = Optional.ofNullable(keys(directory.config()).get(version));
    if (key.isPresent()) {
      return key;
    }
    long now = clock.millis();
    long last = lastKeyReload.get();
    boolean recentlyReloaded =
        last != Long.MIN_VALUE && now - last < KEY_RELOAD_INTERVAL.toMillis();
    if (recentlyReloaded || !lastKeyReload.compareAndSet(last, now)) {
      return Optional.empty();
    }
    return Optional.ofNullable(keys(directory.freshConfig()).get(version));
  }

  static Map<String, byte[]> keys(Entry config) {
    String[] values = config.getAttributeValues(ZimbraAttributes.AUTH_TOKEN_KEY);
    Map<String, byte[]> keys = new HashMap<>();
    if (values == null) {
      return keys;
    }
    for (String value : values) {
      String[] parts = value.split(":");
      if (parts.length != 3) {
        continue;
      }
      try {
        keys.put(Long.toString(Long.parseLong(parts[0])), HexFormat.of().parseHex(parts[2]));
      } catch (IllegalArgumentException e) {
        logger.warn("Ignoring a malformed zimbraAuthTokenKey value");
      }
    }
    return keys;
  }

  static String hmacHex(String data, byte[] key) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
      return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HmacSHA1 unavailable", e);
    }
  }

  private static boolean isRegistered(Entry account, String tokenId) {
    String[] registered = account.getAttributeValues(ZimbraAttributes.AUTH_TOKENS);
    if (registered == null) {
      return false;
    }
    for (String value : registered) {
      int bar = value.indexOf('|');
      if (bar > 0 && value.substring(0, bar).equals(tokenId)) {
        return true;
      }
    }
    return false;
  }

  private boolean validityValueMatches(Entry account, int tokenValue) {
    String enabled =
        directory.config().getAttributeValue(ZimbraAttributes.AUTH_TOKEN_VALIDITY_VALUE_ENABLED);
    if ("FALSE".equalsIgnoreCase(enabled)) {
      return true;
    }
    String accountValue = account.getAttributeValue(ZimbraAttributes.AUTH_TOKEN_VALIDITY_VALUE);
    int expected;
    try {
      expected = accountValue == null ? -1 : Integer.parseInt(accountValue);
    } catch (NumberFormatException e) {
      expected = -1;
    }
    return expected == tokenValue;
  }

  public record VerifiedToken(Entry account, long expiresAt, boolean anyAdmin) {}
}
