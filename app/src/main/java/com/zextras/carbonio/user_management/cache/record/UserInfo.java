// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.cache.record;

public record UserInfo(
    String userId,
    String email,
    String fullName,
    String domain,
    String status,
    String type
) {}
