// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.record;

import java.util.Map;

public record UserMyself(
    String userId,
    String email,
    String fullName,
    String domain,
    String status,
    String type,
    String locale,
    Map<String, Boolean> featureList
) {

  public UserMyself(
      String userId, String email, String fullName, String domain,
      String status, String type, String locale, Map<String, Boolean> featureList) {
    this.userId = userId;
    this.email = email;
    this.fullName = fullName;
    this.domain = domain;
    this.status = status;
    this.type = type;
    this.locale = locale;
    this.featureList = featureList != null ? Map.copyOf(featureList) : Map.of();
  }
}
