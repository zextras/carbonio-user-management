package com.zextras.carbonio.user_management.rights;

import java.util.Optional;

record Ace(
    String grantee,
    String granteeType,
    String right,
    boolean deny,
    boolean subDomain,
    boolean disinheritSubGroups) {

  static final String USER = "usr";
  static final String GROUP = "grp";
  static final String EXTERNAL_GROUP = "egp";
  static final String DOMAIN = "dom";

  static Optional<Ace> parse(String value) {
    if (value == null) {
      return Optional.empty();
    }
    String trimmed = value.trim();
    int last = trimmed.lastIndexOf(' ');
    int middle = last <= 0 ? -1 : trimmed.lastIndexOf(' ', last - 1);
    if (middle <= 0) {
      return Optional.empty();
    }
    String grantee = trimmed.substring(0, middle);
    String granteeType = trimmed.substring(middle + 1, last);
    String rightWithModifiers = trimmed.substring(last + 1);

    char modifier = rightWithModifiers.charAt(0);
    boolean hasModifier = modifier == '-' || modifier == '+' || modifier == '*' || modifier == '^';
    String right = hasModifier ? rightWithModifiers.substring(1) : rightWithModifiers;
    boolean deny = modifier == '-';
    boolean subDomain = modifier == '*';
    boolean disinherit = modifier == '^';
    if (right.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new Ace(grantee, granteeType, right, deny, subDomain, disinherit));
  }
}
