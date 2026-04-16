// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.rest.dto;

import com.zextras.carbonio.user_management.record.UserInfo;

public record UserInfoDto(
    String userId,
    String email,
    String fullName,
    String domain,
    String status,
    String type
) {

  public static UserInfoDto from(UserInfo info) {
    return new UserInfoDto(
        info.userId(), info.email(), info.fullName(),
        info.domain(), info.status(), info.type());
  }
}
