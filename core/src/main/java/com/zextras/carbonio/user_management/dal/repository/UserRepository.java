// SPDX-FileCopyrightText: 2023 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.dal.repository;

import com.zextras.carbonio.user_management.dal.dao.User;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

  /**
   * Returns the user associated with the given id.
   *
   * @param id the user id to retrieve
   * @return an {@link Optional} which contains the {@link User}, or empty if it was not found
   */
  Optional<User> getById(UUID id);

}
