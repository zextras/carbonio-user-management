// SPDX-FileCopyrightText: 2023 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.utilities;

import java.util.Map;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class CookieParserTest {

  static Stream<Arguments> cookiesProvider() {
    return Stream.of(
      Arguments.of("ZM_AUTH_TOKEN=fake-zm-token", Map.of("ZM_AUTH_TOKEN", "fake-zm-token")),
      Arguments.of(
        "ZM_AUTH_TOKEN=fake-zm-token; ZX_AUTH_TOKEN=fake-zx-token",
        Map.of("ZM_AUTH_TOKEN", "fake-zm-token", "ZX_AUTH_TOKEN", "fake-zx-token")
      ),
      Arguments.of(
        "ZM_AUTH_TOKEN=fake-zm-token; ZX_AUTH_TOKEN=fake-zx-token; SESSION=id",
        Map.of("ZM_AUTH_TOKEN", "fake-zm-token", "ZX_AUTH_TOKEN", "fake-zx-token", "SESSION", "id")
      )
    );
  }

  @ParameterizedTest
  @MethodSource("cookiesProvider")
  void givenACookiesStringContainingMultipleCookiesTheTheGetCookiesShouldReturnAMapOfThem(
    String cookies,
    Map<String, String> cookiesMap
  ) {
    // Given & When
    Map<String, String> expectedCookiesMap = CookieParser.getCookies(cookies);

    // Then
    Assertions.assertThat(expectedCookiesMap).isEqualTo(cookiesMap);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "    "})
  void givenAnEmptyCookiesStringTheGetCookiesShouldReturnAnEmptyMap(String cookies) {
    // Given & When
    Map<String, String> expectedCookiesMap = CookieParser.getCookies(cookies);

    // Then
    Assertions.assertThat(expectedCookiesMap).hasSize(0);
  }

  @Test
  void givenACookiesStringTerminatingWithASemicolonTheTheGetCookiesShouldReturnAMapOfThem() {
    // Given
    String cookies = "ZM_AUTH_TOKEN=fake-zm-token;  ";
    // When
    Map<String, String> expectedCookiesMap = CookieParser.getCookies(cookies);

    // Then
    Assertions
      .assertThat(expectedCookiesMap)
      .hasSize(1)
      .containsEntry("ZM_AUTH_TOKEN", "fake-zm-token");
  }
}
