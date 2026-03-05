// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.job;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zextras.carbonio.user_management.repository.UserInfoCacheRepository;
import com.zextras.carbonio.user_management.repository.UserMyselfCacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ExpiredCacheCleanup}.
 * Same package to access package-private constructor and cleanup method.
 */
class ExpiredCacheCleanupTest {

  private UserInfoCacheRepository infoRepo;
  private UserMyselfCacheRepository myselfRepo;
  private ExpiredCacheCleanup cleanup;

  @BeforeEach
  void setUp() {
    infoRepo = mock(UserInfoCacheRepository.class);
    myselfRepo = mock(UserMyselfCacheRepository.class);
    cleanup = new ExpiredCacheCleanup(infoRepo, myselfRepo);
  }

  @Test
  void callsDeleteExpiredOnBothRepositories() {
    when(infoRepo.deleteExpired()).thenReturn(5);
    when(myselfRepo.deleteExpired()).thenReturn(3);

    cleanup.cleanup();

    verify(infoRepo).deleteExpired();
    verify(myselfRepo).deleteExpired();
  }

  @Test
  void infoRepoFailureDoesNotPreventMyselfCleanup() {
    when(infoRepo.deleteExpired()).thenThrow(new RuntimeException("db error"));
    when(myselfRepo.deleteExpired()).thenReturn(2);

    cleanup.cleanup();

    verify(infoRepo).deleteExpired();
    verify(myselfRepo).deleteExpired();
  }

  @Test
  void myselfRepoFailureDoesNotPreventInfoCleanup() {
    when(infoRepo.deleteExpired()).thenReturn(3);
    when(myselfRepo.deleteExpired()).thenThrow(new RuntimeException("db error"));

    cleanup.cleanup();

    verify(infoRepo).deleteExpired();
    verify(myselfRepo).deleteExpired();
  }

  @Test
  void bothReposFailDoesNotThrow() {
    when(infoRepo.deleteExpired()).thenThrow(new RuntimeException("error1"));
    when(myselfRepo.deleteExpired()).thenThrow(new RuntimeException("error2"));

    assertDoesNotThrow(() -> cleanup.cleanup());
  }

  @Test
  void noDeletedEntriesDoesNotThrow() {
    when(infoRepo.deleteExpired()).thenReturn(0);
    when(myselfRepo.deleteExpired()).thenReturn(0);

    assertDoesNotThrow(() -> cleanup.cleanup());
  }
}
