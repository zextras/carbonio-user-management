// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.rest.dto;

import com.zextras.carbonio.user_management.record.UserMyself;
import java.util.Map;

public record MyselfDto(
    UserInfoDto info,
    String locale,
    Map<String, Boolean> featureList
) {

  public static MyselfDto from(UserMyself myself) {
    UserInfoDto info = new UserInfoDto(
        myself.userId(), myself.email(), myself.fullName(),
        myself.domain(), myself.status(), myself.type());
    return new MyselfDto(info, myself.locale(), myself.featureList());
  }
}
