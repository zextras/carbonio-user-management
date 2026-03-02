// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.rest.dto;

import com.zextras.carbonio.user_management.service.UserService.MyselfResult;
import java.util.Map;

public record MyselfDto(
    UserInfoDto info,
    String locale,
    Map<String, Boolean> featureList
) {

  public static MyselfDto from(MyselfResult result) {
    return new MyselfDto(
        UserInfoDto.from(result.info()),
        result.details().locale(),
        result.details().featureList());
  }
}
