package com.zextras.carbonio.user_management.dal.repository.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.zextras.carbonio.user_management.core.annotations.UnitTest;
import com.zextras.carbonio.user_management.dal.dao.User;
import io.ebean.Database;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@UnitTest
class UserRepositoryEbeanTest {

  private final UserRepositoryEbean userRepositoryEbean;
  private final Database            database;

  public UserRepositoryEbeanTest() {
    this.database = mock(Database.class, RETURNS_DEEP_STUBS);
    this.userRepositoryEbean = new UserRepositoryEbean(database);
  }

  @AfterEach
  public void cleanup() {
    reset(database);
  }

  @Nested
  @DisplayName("Get by id test")
  class GetByIdTests {

    @Test
    @DisplayName("Retrieves a user by it's id")
    public void getById_testOK() {
      UUID userId = UUID.randomUUID();

      when(database.find(User.class).where().eq("id", userId).findOneOrEmpty()).thenReturn(
        Optional.of(User.create().id(userId.toString())));

      Optional<User> user = userRepositoryEbean.getById(userId);

      assertTrue(user.isPresent());
      assertEquals(userId.toString(), user.get().getId());
    }

    @Test
    @DisplayName("Returns an empty optional if the user was not found")
    public void getById_testNotFound() {
      UUID userId = UUID.randomUUID();

      when(database.find(User.class).where().eq("id", userId).findOneOrEmpty()).thenReturn(
        Optional.empty());
      Optional<User> user = userRepositoryEbean.getById(userId);

      assertTrue(user.isEmpty());
    }
  }

}