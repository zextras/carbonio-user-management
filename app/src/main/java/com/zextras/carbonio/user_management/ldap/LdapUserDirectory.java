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
    return directory
        .accountById(userId, LdapDirectory.ACCOUNT_INFO_ATTRIBUTES)
        .map(resolver::resolve)
        .map(UserMapper::toUserInfo);
  }

  @Override
  public Optional<UserInfo> byEmail(String email) {
    return directory
        .accountByEmail(email, LdapDirectory.ACCOUNT_INFO_ATTRIBUTES)
        .map(resolver::resolve)
        .map(UserMapper::toUserInfo);
  }

  @Override
  public List<UserInfo> byIds(List<String> userIds) {
    return resolver
        .resolveAll(directory.accountsByIds(userIds, LdapDirectory.ACCOUNT_INFO_ATTRIBUTES))
        .stream()
        .map(UserMapper::toUserInfo)
        .toList();
  }

  @Override
  public Optional<AuthenticatedUser> byToken(String token) {
    return tokenVerifier
        .verify(token, LdapDirectory.ACCOUNT_MYSELF_ATTRIBUTES)
        .map(
            verified ->
                new AuthenticatedUser(
                    UserMapper.toUserMyself(
                        resolver.resolveForMyself(verified.account()), directory::serverByName),
                    verified.expiresAt() - clock.millis()));
  }
}
