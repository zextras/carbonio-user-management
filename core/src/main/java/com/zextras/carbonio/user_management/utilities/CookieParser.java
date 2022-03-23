// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.utilities;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class CookieParser {

  public static Map<String, String> getCookies(String cookies) {
    return Arrays
      .stream(cookies.split(";", -1))
      .map(cookie -> cookie.split("=", -1))
      .collect(Collectors.toMap(
        cookie -> cookie[0].trim(),
        cookie -> cookie[1].trim()
      ));
  }
}
