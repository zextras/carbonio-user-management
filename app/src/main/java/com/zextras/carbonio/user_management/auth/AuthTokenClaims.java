package com.zextras.carbonio.user_management.auth;

import java.util.Map;

record AuthTokenClaims(
    String accountId,
    Long expiresAt,
    String type,
    String tokenId,
    int validityValue,
    boolean anyAdmin) {

  static AuthTokenClaims from(Map<String, String> claims) {
    return new AuthTokenClaims(
        claims.get("id"),
        parseLong(claims.get("exp")),
        claims.get("type"),
        String.valueOf(parseInt(claims.get("tid"))),
        parseInt(claims.get("vv")),
        "1".equals(claims.get("admin"))
            || "1".equals(claims.get("domain"))
            || "1".equals(claims.get("dlgadmin")));
  }

  boolean isZimbraUser() {
    return type == null || type.isEmpty() || "zimbra".equals(type);
  }

  private static Long parseLong(String value) {
    try {
      return value == null ? null : Long.parseLong(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static int parseInt(String value) {
    try {
      return value == null ? -1 : Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return -1;
    }
  }
}
