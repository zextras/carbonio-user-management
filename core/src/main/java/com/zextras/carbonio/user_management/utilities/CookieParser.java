// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.utilities;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

public class CookieParser {

  /**
   * Allows to convert a {@link String} containing a list of cookies (it can be empty) to a
   * {@link Map} where each key/value entry represent a cookie.
   *
   * @param cookies a {@link String} representing a list of cookies with this format:
   *                <code>token=value; token2=value2</code>.
   * @return a {@link Map} where each entry represent the key/value of a cookie.
   */
  public static Map<String, String> getCookies(String cookies) {
    if (cookies.isEmpty()) {
      return Collections.emptyMap();
    }

    return Arrays
      .stream(cookies.split(";", -1))
      .filter(cookie -> !cookie.isBlank())
      .map(cookie -> cookie.split("=", -1))
      .collect(Collectors.toMap(
        cookie -> cookie[0].trim(),
        cookie -> cookie[1].trim()
      ));
  }
}
