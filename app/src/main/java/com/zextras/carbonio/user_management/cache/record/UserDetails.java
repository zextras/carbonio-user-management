// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.cache.record;

import java.util.Map;

public record UserDetails(
    String locale,
    Map<String, Boolean> featureList
) {}
