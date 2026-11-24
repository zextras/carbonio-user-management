package com.zextras.carbonio.user_management.rights;

import com.unboundid.ldap.sdk.Entry;
import com.zextras.carbonio.user_management.UserManagementServiceConfig.ZimbraAttributes;
import com.zextras.carbonio.user_management.auth.AuthTokenVerifier;
import com.zextras.carbonio.user_management.ldap.LdapDirectory;
import com.zextras.carbonio.user_management.rights.RightsEvaluator.Target;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class RightsService {

  static final String WRITE_RIGHT = "modifyAccount";
  static final String CONFIGURE_RIGHT = "setDomainAdminAccountAndCalendarResourceAttrs";

  private final LdapDirectory directory;
  private final RightsCatalog catalog;
  private final RightsEvaluator evaluator;
  private final AuthTokenVerifier tokenVerifier;

  @Inject
  public RightsService(
      LdapDirectory directory,
      RightsCatalog catalog,
      RightsEvaluator evaluator,
      AuthTokenVerifier tokenVerifier) {
    this.directory = directory;
    this.catalog = catalog;
    this.evaluator = evaluator;
    this.tokenVerifier = tokenVerifier;
  }

  public static class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
      super(message);
    }
  }

  public static class InvalidTokenException extends RuntimeException {
    public InvalidTokenException() {
      super("Invalid or expired token");
    }
  }

  public record AccountRights(
      String actorId,
      String targetId,
      boolean isGlobalAdmin,
      boolean isDelegatedAdmin,
      boolean canWrite,
      boolean canConfigure) {}

  public List<Right> checkableOn(String targetType) {
    requireSupportedTargetType(targetType);
    return catalog.checkableOn(targetType).stream().filter(r -> !r.userRight()).toList();
  }

  public String description(Right right) {
    return catalog.description(right);
  }

  public boolean checkWithToken(
      String token, String rightName, String targetType, String targetId, String targetName) {
    Right right = right(rightName, targetType);
    AuthTokenVerifier.VerifiedToken verified =
        tokenVerifier.verify(token).orElseThrow(InvalidTokenException::new);
    return target(targetType, targetId, targetName)
        .map(target -> evaluator.check(verified.account(), verified.anyAdmin(), right, target))
        .orElse(false);
  }

  public boolean checkWithAccountId(
      String accountId, String rightName, String targetType, String targetId, String targetName) {
    Right right = right(rightName, targetType);
    Optional<Entry> actor = directory.accountById(accountId);
    if (actor.isEmpty()) {
      return false;
    }
    return target(targetType, targetId, targetName)
        .map(
            target ->
                evaluator.check(actor.get(), evaluator.isAdequateAdmin(actor.get()), right, target))
        .orElse(false);
  }

  public Optional<AccountRights> accountRights(String actorId, String targetId) {
    Optional<Entry> actor = directory.accountById(actorId);
    Optional<Entry> target = directory.accountById(targetId);
    if (actor.isEmpty() || target.isEmpty()) {
      return Optional.empty();
    }
    boolean asAdmin = evaluator.isAdequateAdmin(actor.get());
    Target account = new Target("account", target.get());
    return Optional.of(
        new AccountRights(
            actorId,
            targetId,
            isTrue(actor.get(), ZimbraAttributes.IS_ADMIN_ACCOUNT),
            isTrue(actor.get(), ZimbraAttributes.IS_DELEGATED_ADMIN_ACCOUNT),
            evaluator.check(actor.get(), asAdmin, catalogRight(WRITE_RIGHT), account),
            evaluator.check(actor.get(), asAdmin, catalogRight(CONFIGURE_RIGHT), account)));
  }

  private static boolean isTrue(Entry entry, String attribute) {
    return "TRUE".equalsIgnoreCase(entry.getAttributeValue(attribute));
  }

  private Right catalogRight(String name) {
    return catalog
        .find(name)
        .orElseThrow(() -> new IllegalStateException("Right missing from the catalog: " + name));
  }

  private Right right(String rightName, String targetType) {
    requireSupportedTargetType(targetType);
    Right right =
        catalog
            .find(rightName)
            .orElseThrow(() -> new InvalidRequestException("Unknown right: " + rightName));
    if (right.userRight()) {
      throw new InvalidRequestException("User rights are not supported: " + rightName);
    }
    if (!right.executableOn(targetType)) {
      throw new InvalidRequestException(
          "Right " + rightName + " cannot be checked on " + targetType);
    }
    return right;
  }

  private void requireSupportedTargetType(String targetType) {
    if (!catalog.isTargetType(targetType)) {
      throw new InvalidRequestException("Unknown target type: " + targetType);
    }
    if (!RightsEvaluator.SUPPORTED_TARGET_TYPES.contains(targetType)) {
      throw new InvalidRequestException("Unsupported target type: " + targetType);
    }
  }

  private Optional<Target> target(String targetType, String id, String name) {
    boolean byName = name != null && !name.isEmpty();
    String key = byName ? name : id;
    if (key == null || key.isEmpty()) {
      throw new InvalidRequestException("Missing target id or name for " + targetType);
    }
    Optional<Entry> entry =
        switch (targetType) {
          case "account" -> byName ? directory.accountByEmail(key) : directory.accountById(key);
          case "domain" ->
              byName ? directory.domainByName(key) : directory.domainById(key);
          case "cos" -> byName ? directory.cosByName(key) : directory.cosById(key);
          default -> throw new InvalidRequestException("Unsupported target type: " + targetType);
        };
    return entry.map(e -> new Target(targetType, e));
  }
}
