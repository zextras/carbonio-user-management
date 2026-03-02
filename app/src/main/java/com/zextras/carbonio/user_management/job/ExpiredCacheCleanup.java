// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.cache;

import com.zextras.carbonio.user_management.cache.repository.UserDetailsCacheRepository;
import com.zextras.carbonio.user_management.cache.repository.UserInfoCacheRepository;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.jboss.logging.Logger;

/**
 * Periodically removes expired rows from the shared PostgreSQL cache tables.
 *
 * <p>Runs every hour via a daemon {@link ScheduledExecutorService} (no dependency on
 * {@code quarkus-scheduler}). The cleanup is not critical for correctness — both tables are
 * bounded (max 1 row per user) — it's purely hygienic.
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

  private static final Logger LOG = Logger.getLogger(ExpiredCacheCleanup.class);

  private final UserInfoCacheRepository userInfoRepository;
  private final UserDetailsCacheRepository userDetailsRepository;
  private final ScheduledExecutorService scheduler;

  @Inject
  ExpiredCacheCleanup(
      UserInfoCacheRepository userInfoRepository,
      UserDetailsCacheRepository userDetailsRepository) {
    this.userInfoRepository = userInfoRepository;
    this.userDetailsRepository = userDetailsRepository;
    this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "cache-cleanup");
      t.setDaemon(true);
      return t;
    });
  }

  void onStart(@Observes StartupEvent event) {
    scheduler.scheduleAtFixedRate(this::cleanup, 1, 1, TimeUnit.HOURS);
  }

  void onStop(@Observes ShutdownEvent event) {
    scheduler.shutdown();
  }

  private void cleanup() {
    try {
      int infoDeleted = userInfoRepository.deleteExpired();
      int detailsDeleted = userDetailsRepository.deleteExpired();
      if (infoDeleted > 0 || detailsDeleted > 0) {
        LOG.infof("Persistent cache cleanup: removed %d user_info and %d user_details expired entries",
            infoDeleted, detailsDeleted);
      }
    } catch (Exception e) {
      LOG.warn("Persistent cache cleanup failed", e);
    }
  }
}
