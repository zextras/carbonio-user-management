package com.zextras.carbonio.user_management.dal.repository.impl;

import com.zextras.carbonio.user_management.dal.dao.User;
import com.zextras.carbonio.user_management.dal.repository.UserRepository;
import io.ebean.Database;
import java.util.Optional;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.transaction.Transactional;

@Transactional
@Singleton
public class UserRepositoryEbean implements UserRepository {

  private final Database database;

  @Inject
  public UserRepositoryEbean(Database database) {
    this.database = database;
  }

  @Override
  public Optional<User> getById(UUID id) {
    return database.find(User.class)
      .where()
      .eq("id", id)
      .findOneOrEmpty();
  }
}
