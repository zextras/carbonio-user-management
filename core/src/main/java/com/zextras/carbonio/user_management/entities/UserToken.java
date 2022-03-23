// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.entities;

import java.util.Objects;
import java.util.UUID;

public class UserToken {

  private final String token;
  private final UUID   userId;
  private final Long   lifeTimeInMillis;

  public UserToken(
    String token,
    UUID userId,
    Long lifeTime
  ) {
    this.token = token;
    this.userId = userId;
    this.lifeTimeInMillis = lifeTime;
  }

  public String getToken() {
    return token;
  }

  public UUID getUserId() {
    return userId;
  }

  public Long getLifeTimeInMillis() {
    return lifeTimeInMillis;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    UserToken userToken = (UserToken) o;
    return Objects.equals(token, userToken.token) && Objects.equals(userId, userToken.userId)
      && Objects.equals(lifeTimeInMillis, userToken.lifeTimeInMillis);
  }

  @Override
  public int hashCode() {
    return Objects.hash(token, userId, lifeTimeInMillis);
  }
}
