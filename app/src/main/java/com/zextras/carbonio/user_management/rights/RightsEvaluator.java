package com.zextras.carbonio.user_management.rights;

import com.unboundid.ldap.sdk.Entry;
import com.zextras.carbonio.user_management.UserManagementServiceConfig.ZimbraAttributes;
import com.zextras.carbonio.user_management.ldap.AccountResolver;
import com.zextras.carbonio.user_management.ldap.LdapDirectory;
import com.zextras.carbonio.user_management.rights.GroupMemberships.Membership;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Mirrors mailbox HardRules, CheckPresetRight and CheckAttrRight for admin rights.
@ApplicationScoped
public class RightsEvaluator {

  public static final Set<String> SUPPORTED_TARGET_TYPES = Set.of("account", "domain", "cos");

  private final LdapDirectory directory;
  private final RightsCatalog catalog;

  @Inject
  public RightsEvaluator(LdapDirectory directory, RightsCatalog catalog) {
    this.directory = directory;
    this.catalog = catalog;
  }

  public record Target(String type, Entry entry) {}

  public boolean isAdequateAdmin(Entry account) {
    return isTrue(account, ZimbraAttributes.IS_ADMIN_ACCOUNT)
        || isTrue(account, ZimbraAttributes.IS_DELEGATED_ADMIN_ACCOUNT);
  }

  public boolean check(Entry actor, boolean asAdmin, Right right, Target target) {
    if (right.userRight()) {
      throw new IllegalArgumentException("User rights are not supported: " + right.name());
    }
    if (!SUPPORTED_TARGET_TYPES.contains(target.type())) {
      throw new IllegalArgumentException("Unsupported target type: " + target.type());
    }
    Optional<Entry> targetDomain = targetDomain(target);
    if (asAdmin && targetDomain.map(RightsEvaluator::inaccessible).orElse(false)) {
      return false;
    }
    if (asAdmin && isTrue(actor, ZimbraAttributes.IS_ADMIN_ACCOUNT)) {
      return true;
    }
    if (!(asAdmin && isTrue(actor, ZimbraAttributes.IS_DELEGATED_ADMIN_ACCOUNT))) {
      return false;
    }
    if ("account".equals(target.type())
        && isTrue(target.entry(), ZimbraAttributes.IS_ADMIN_ACCOUNT)) {
      return false;
    }
    Grantee grantee = grantee(actor);
    List<Level> levels = levels(grantee, target, targetDomain);
    return switch (right) {
      case Right.Preset preset -> preset(grantee, levels, preset, target.type());
      case Right.Attr attr -> attr(grantee, levels, attr, target.type());
      case Right.Combo combo ->
          catalog.presetRights(combo).stream()
                  .allMatch(p -> preset(grantee, levels, p, target.type()))
              && catalog.attrRights(combo).stream()
                  .allMatch(a -> attr(grantee, levels, a, target.type()));
    };
  }

  private record Grantee(String id, Set<String> adminGroupIds, Optional<Entry> domain) {}

  private record Level(List<Ace> aces, boolean subDomain) {}

  private Grantee grantee(Entry actor) {
    String name = AccountResolver.accountName(actor);
    Set<String> adminGroups =
        GroupMemberships.of(directory, actor, name).stream()
            .filter(Membership::adminGroup)
            .map(Membership::id)
            .collect(Collectors.toUnmodifiableSet());
    return new Grantee(
        actor.getAttributeValue(ZimbraAttributes.ID), adminGroups, domainOfName(name));
  }

  private Optional<Entry> targetDomain(Target target) {
    return switch (target.type()) {
      case "account" -> domainOfName(AccountResolver.accountName(target.entry()));
      case "domain" -> Optional.of(target.entry());
      default -> Optional.empty();
    };
  }

  private List<Level> levels(Grantee grantee, Target target, Optional<Entry> targetDomain) {
    List<Level> levels = new ArrayList<>();
    addLevel(levels, aces(target.entry()), false);
    switch (target.type()) {
      case "account" -> {
        addLevel(levels, groupAces(grantee, target.entry(), targetDomain), false);
        targetDomain.ifPresent(domain -> addLevel(levels, aces(domain), false));
      }
      case "domain" -> {
        for (Entry parent : parentDomains(target.entry())) {
          addLevel(levels, aces(parent), true);
        }
      }
      default -> {}
    }
    directory.globalGrant().ifPresent(global -> addLevel(levels, aces(global), false));
    return levels;
  }

  private static void addLevel(List<Level> levels, List<Ace> aces, boolean subDomain) {
    if (!aces.isEmpty()) {
      levels.add(new Level(aces, subDomain));
    }
  }

  private List<Ace> groupAces(Grantee grantee, Entry account, Optional<Entry> targetDomain) {
    List<Ace> denied = new ArrayList<>();
    List<Ace> allowed = new ArrayList<>();
    String name = AccountResolver.accountName(account);
    for (Membership membership : GroupMemberships.of(directory, account, name)) {
      boolean skipPositive = !crossDomainOk(grantee, targetDomain, membership.group());
      for (Ace ace : aces(membership.group())) {
        if (ace.disinheritSubGroups() && !membership.directStatic()) {
          continue;
        }
        if (ace.deny()) {
          denied.add(ace);
        } else if (!skipPositive) {
          allowed.add(ace);
        }
      }
    }
    return Stream.concat(denied.stream(), allowed.stream()).toList();
  }

  private boolean crossDomainOk(Grantee grantee, Optional<Entry> targetDomain, Entry group) {
    if (targetDomain.isEmpty()) {
      return true;
    }
    Optional<Entry> groupDomain = domainOfName(groupName(group));
    if (groupDomain.isEmpty()) {
      return false;
    }
    String targetDomainId = id(targetDomain.get());
    if (grantee.domain().map(RightsEvaluator::id).filter(targetDomainId::equals).isPresent()) {
      return true;
    }
    if (targetDomainId.equals(id(groupDomain.get()))) {
      return true;
    }
    String granteeDomainId = grantee.domain().map(RightsEvaluator::id).orElse(null);
    for (Ace ace : aces(targetDomain.get())) {
      if ("crossDomainAdmin".equals(ace.right())
          && Ace.DOMAIN.equals(ace.granteeType())
          && ace.grantee().equals(granteeDomainId)) {
        return !ace.deny();
      }
    }
    return false;
  }

  private boolean preset(Grantee grantee, List<Level> levels, Right.Preset needed, String targetType) {
    if (!needed.executableOn(targetType)) {
      return false;
    }
    for (Level level : levels) {
      Boolean individual =
          presetDecision(level, ace -> Ace.USER.equals(ace.granteeType()) && ace.grantee().equals(grantee.id()), needed);
      if (individual != null) {
        return individual;
      }
      Boolean group = presetDecision(level, ace -> groupMatch(grantee, ace), needed);
      if (group != null) {
        return group;
      }
    }
    return false;
  }

  private Boolean presetDecision(
      Level level, Predicate<Ace> granteeMatch, Right.Preset needed) {
    boolean allow = false;
    for (Ace ace : level.aces()) {
      if (!granteeMatch.test(ace) || !grants(ace, needed)) {
        continue;
      }
      if (ace.deny()) {
        return Boolean.FALSE;
      }
      if (ace.subDomain() == level.subDomain()) {
        allow = true;
      }
    }
    return allow ? Boolean.TRUE : null;
  }

  private boolean grants(Ace ace, Right.Preset needed) {
    return catalog
        .find(ace.right())
        .map(
            granted ->
                switch (granted) {
                  case Right.Preset preset -> preset.name().equals(needed.name());
                  case Right.Combo combo -> catalog.presetRights(combo).contains(needed);
                  case Right.Attr ignored -> false;
                })
        .orElse(false);
  }

  private static boolean groupMatch(Grantee grantee, Ace ace) {
    if (Ace.GROUP.equals(ace.granteeType())) {
      return grantee.adminGroupIds().contains(ace.grantee());
    }
    return Ace.EXTERNAL_GROUP.equals(ace.granteeType()) && ace.deny();
  }

  private enum Collected {
    SOME,
    ALLOW_ALL,
    DENY_ALL
  }

  private boolean attr(Grantee grantee, List<Level> levels, Right.Attr needed, String targetType) {
    if (!needed.executableOn(targetType)) {
      return false;
    }
    Map<String, Integer> allowSome = new HashMap<>();
    Map<String, Integer> denySome = new HashMap<>();
    Collected collected = Collected.SOME;
    int relativity = 1;
    for (Level level : levels) {
      collected =
          expand(
              level,
              ace -> Ace.USER.equals(ace.granteeType()) && ace.grantee().equals(grantee.id()),
              relativity,
              needed,
              targetType,
              allowSome,
              denySome);
      if (collected == Collected.SOME) {
        collected =
            expand(
                level,
                ace -> groupMatch(grantee, ace),
                relativity + 1,
                needed,
                targetType,
                allowSome,
                denySome);
      }
      relativity += 2;
      if (collected != Collected.SOME) {
        break;
      }
    }
    return covers(collected, allowSome, denySome, needed);
  }

  private Collected expand(
      Level level,
      Predicate<Ace> granteeMatch,
      int relativity,
      Right.Attr needed,
      String targetType,
      Map<String, Integer> allowSome,
      Map<String, Integer> denySome) {
    for (Ace ace : level.aces()) {
      if (!granteeMatch.test(ace)) {
        continue;
      }
      if (!ace.deny() && ace.subDomain() != level.subDomain()) {
        continue;
      }
      Optional<Right> granted = catalog.find(ace.right());
      if (granted.isEmpty()) {
        continue;
      }
      List<Right.Attr> attrRights =
          switch (granted.get()) {
            case Right.Attr attr -> List.of(attr);
            case Right.Combo combo -> catalog.attrRights(combo);
            case Right.Preset ignored -> List.of();
          };
      for (Right.Attr attrRight : attrRights) {
        Collected result =
            expandAttrRight(ace, attrRight, relativity, needed, targetType, allowSome, denySome);
        if (result != Collected.SOME) {
          return result;
        }
      }
    }
    return Collected.SOME;
  }

  private static Collected expandAttrRight(
      Ace ace,
      Right.Attr granted,
      int relativity,
      Right.Attr needed,
      String targetType,
      Map<String, Integer> allowSome,
      Map<String, Integer> denySome) {
    if (!granted.suitableFor(needed.set()) || !granted.executableOn(targetType)) {
      return Collected.SOME;
    }
    if (granted.targetTypes().stream().noneMatch(needed.targetTypes()::contains)) {
      return Collected.SOME;
    }
    if (granted.allAttrs()) {
      if (ace.deny()) {
        return granted.set() == needed.set() ? Collected.DENY_ALL : Collected.SOME;
      }
      return Collected.ALLOW_ALL;
    }
    if (ace.deny() && granted.set() != needed.set()) {
      return Collected.SOME;
    }
    Map<String, Integer> target = ace.deny() ? denySome : allowSome;
    for (String attr : granted.attrs()) {
      target.merge(attr, relativity, Math::min);
    }
    return Collected.SOME;
  }

  private static boolean covers(
      Collected collected,
      Map<String, Integer> allowSome,
      Map<String, Integer> denySome,
      Right.Attr needed) {
    if (collected == Collected.ALLOW_ALL && denySome.isEmpty()) {
      return true;
    }
    if (collected == Collected.DENY_ALL && allowSome.isEmpty()) {
      return false;
    }
    if (needed.allAttrs()) {
      return false;
    }
    Set<String> neededAttrs = needed.attrs();
    return switch (collected) {
      case ALLOW_ALL -> neededAttrs.stream().noneMatch(denySome::containsKey);
      case DENY_ALL -> allowSome.keySet().containsAll(neededAttrs);
      case SOME -> {
        Set<String> allowed = new HashSet<>(allowSome.keySet());
        allowSome.forEach(
            (attr, allowRelativity) -> {
              Integer denyRelativity = denySome.get(attr);
              if (denyRelativity != null && denyRelativity <= allowRelativity) {
                allowed.remove(attr);
              }
            });
        yield allowed.containsAll(neededAttrs);
      }
    };
  }

  private List<Entry> parentDomains(Entry domain) {
    String name = domain.getAttributeValue(ZimbraAttributes.DOMAIN_NAME);
    List<Entry> parents = new ArrayList<>();
    if (name == null) {
      return parents;
    }
    int dot = name.indexOf('.');
    while (dot >= 0) {
      name = name.substring(dot + 1);
      directory.domainByName(name).ifPresent(parents::add);
      dot = name.indexOf('.');
    }
    return parents;
  }

  private Optional<Entry> domainOfName(String name) {
    int at = name == null ? -1 : name.lastIndexOf('@');
    return at < 0 ? Optional.empty() : directory.domainByName(name.substring(at + 1));
  }

  private static String groupName(Entry group) {
    return Stream.of(
            group.getAttributeValue(ZimbraAttributes.MAIL_DELIVERY_ADDRESS),
            group.getAttributeValue(ZimbraAttributes.MAIL))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  private static List<Ace> aces(Entry entry) {
    String[] values = entry.getAttributeValues(ZimbraAttributes.ACE);
    if (values == null) {
      return List.of();
    }
    List<Ace> parsed =
        Arrays.stream(values).map(Ace::parse).flatMap(Optional::stream).toList();
    return Stream.concat(parsed.stream().filter(Ace::deny), parsed.stream().filter(a -> !a.deny()))
        .toList();
  }

  private static boolean inaccessible(Entry domain) {
    String status = domain.getAttributeValue(ZimbraAttributes.DOMAIN_STATUS);
    if (status == null) {
      return false;
    }
    String normalized = status.toLowerCase(Locale.ROOT);
    return "suspended".equals(normalized) || "shutdown".equals(normalized);
  }

  private static String id(Entry entry) {
    return entry.getAttributeValue(ZimbraAttributes.ID);
  }

  private static boolean isTrue(Entry entry, String attribute) {
    return "TRUE".equalsIgnoreCase(entry.getAttributeValue(attribute));
  }
}
