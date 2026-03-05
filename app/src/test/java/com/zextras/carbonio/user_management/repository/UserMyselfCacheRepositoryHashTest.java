// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests for the package-private {@code hashToken} method in {@link UserMyselfCacheRepository}.
 * Same package to access the package-private static method.
 */
class UserMyselfCacheRepositoryHashTest {

  @Test
  void hashTokenReturnsSha256HexString() {
    String hash = UserMyselfCacheRepository.hashToken("test-token");

    assertThat(hash).hasSize(64); // SHA-256 = 32 bytes = 64 hex chars
    assertThat(hash).matches("[0-9a-f]+");
  }

  @Test
  void hashTokenIsDeterministic() {
    String hash1 = UserMyselfCacheRepository.hashToken("same-token");
    String hash2 = UserMyselfCacheRepository.hashToken("same-token");

    assertThat(hash1).isEqualTo(hash2);
  }

  @Test
  void differentTokensProduceDifferentHashes() {
    String hash1 = UserMyselfCacheRepository.hashToken("token-a");
    String hash2 = UserMyselfCacheRepository.hashToken("token-b");

    assertThat(hash1).isNotEqualTo(hash2);
  }

  @Test
  void emptyTokenProducesValidHash() {
    String hash = UserMyselfCacheRepository.hashToken("");

    assertThat(hash).hasSize(64);
    assertThat(hash).matches("[0-9a-f]+");
  }

  @Test
  void hashTokenHandlesUnicodeInput() {
    String hash = UserMyselfCacheRepository.hashToken("token-con-àccénti-日本語");

    assertThat(hash).hasSize(64);
    assertThat(hash).matches("[0-9a-f]+");
  }

  @Test
  void knownSha256Value() {
    // SHA-256("hello") = 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
    String hash = UserMyselfCacheRepository.hashToken("hello");

    assertThat(hash).isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
  }
}
