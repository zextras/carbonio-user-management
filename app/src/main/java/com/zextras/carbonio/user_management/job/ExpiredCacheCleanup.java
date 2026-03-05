// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.job;

import com.zextras.carbonio.user_management.repository.UserInfoCacheRepository;
import com.zextras.carbonio.user_management.repository.UserMyselfCacheRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Periodically removes expired rows from the shared PostgreSQL cache tables.
 *
 * <p>Runs every hour (first run after 5 minutes) via Quarkus {@code @Scheduled}, which ensures
 * proper CDI context and transaction management on the scheduler thread.
 *
 * <p>The cleanup is not critical for correctness — both tables are bounded (max 1 row per user)
 * — it's purely hygienic.
 *
 * <p><b>Concurrency across instances:</b> Multiple instances may trigger cleanup simultaneously.
 * This is safe because {@code DELETE WHERE expires_at <= :now} is idempotent and PostgreSQL
 * uses row-level locking: concurrent DELETEs on the same rows simply result in one transaction
 * deleting the rows and the other finding zero matching rows. No advisory locks are needed.
 *
 * <p><b>Concurrency with upserts:</b> If an upsert updates a row's {@code expires_at} to a
 * future value while cleanup runs, PostgreSQL's row-level lock ensures the DELETE sees the
 * updated value and skips the row. The row survives correctly.
 */
@ApplicationScoped
public class ExpiredCacheCleanup {

  private static final Logger LOG = LoggerFactory.getLogger(ExpiredCacheCleanup.class);

  private final UserInfoCacheRepository userInfoRepository;
  private final UserMyselfCacheRepository userMyselfRepository;

  @Inject
  ExpiredCacheCleanup(
      UserInfoCacheRepository userInfoRepository,
      UserMyselfCacheRepository userMyselfRepository) {
    this.userInfoRepository = userInfoRepository;
    this.userMyselfRepository = userMyselfRepository;
  }

  @Scheduled(every = "1h", delayed = "5m")
  void cleanup() {
    int infoDeleted = 0;
    int myselfDeleted = 0;
    try {
      infoDeleted = userInfoRepository.deleteExpired();
    } catch (Exception e) {
      LOG.warn("user_info_cache cleanup failed", e);
    }
    try {
      myselfDeleted = userMyselfRepository.deleteExpired();
    } catch (Exception e) {
      LOG.warn("user_myself_cache cleanup failed", e);
    }
    if (infoDeleted > 0 || myselfDeleted > 0) {
      LOG.info("Persistent cache cleanup: removed {} user_info and {} user_myself expired entries",
          infoDeleted, myselfDeleted);
    }
  }
}
