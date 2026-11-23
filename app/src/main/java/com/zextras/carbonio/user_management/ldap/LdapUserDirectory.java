package com.zextras.carbonio.user_management.ldap;

import com.zextras.carbonio.user_management.auth.AuthTokenVerifier;
import com.zextras.carbonio.user_management.record.UserInfo;
import com.zextras.carbonio.user_management.service.UserDirectory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class LdapUserDirectory implements UserDirectory {

  private final LdapDirectory directory;
  private final AccountResolver resolver;
  private final AuthTokenVerifier tokenVerifier;
  private final Clock clock;

  @Inject
  public LdapUserDirectory(
      LdapDirectory directory,
      AccountResolver resolver,
      AuthTokenVerifier tokenVerifier,
      Clock clock) {
    this.directory = directory;
    this.resolver = resolver;
    this.tokenVerifier = tokenVerifier;
    this.clock = clock;
  }

  @Override
  public Optional<UserInfo> byId(String userId) {
    return directory.accountById(userId).map(resolver::resolve).map(UserMapper::toUserInfo);
  }

  @Override
  public Optional<UserInfo> byEmail(String email) {
    return directory.accountByEmail(email).map(resolver::resolve).map(UserMapper::toUserInfo);
  }

  @Override
  public List<UserInfo> byIds(List<String> userIds) {
    return directory.accountsByIds(userIds).stream()
        .map(resolver::resolve)
        .map(UserMapper::toUserInfo)
        .toList();
  }

  @Override
  public Optional<AuthenticatedUser> byToken(String token) {
    return tokenVerifier
        .verify(token)
        .map(
            verified ->
                new AuthenticatedUser(
                    UserMapper.toUserMyself(
                        resolver.resolve(verified.account()), directory::serverByName),
                    verified.expiresAt() - clock.millis()));
  }
}
