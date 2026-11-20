// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management;

import com.github.benmanes.caffeine.cache.Ticker;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.time.Clock;

@Singleton
public class ClockProducer {

  @Produces
  @Singleton
  Clock clock() {
    return Clock.systemUTC();
  }

  @Produces
  @Singleton
  Ticker ticker() {
    return Ticker.systemTicker();
  }
}
