/*
 * SPDX-FileCopyrightText: 2025 Zextras <https://www.zextras.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.zextras.carbonio.user_management.systemd;

import com.zextras.carbonio.systemd.SystemdNotify;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/** Signals systemd readiness when the Quarkus HTTP server has fully started. */
@ApplicationScoped
public class SystemdReadinessNotifier {

  void onStart(@Observes StartupEvent ev) {
    SystemdNotify.ready("user-management ready");
  }
}
