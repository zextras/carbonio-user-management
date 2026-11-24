package com.zextras.carbonio.user_management.rights;

import com.unboundid.ldap.sdk.Entry;
import com.zextras.carbonio.user_management.UserManagementServiceConfig.ZimbraAttributes;
import com.zextras.carbonio.user_management.ldap.AccountResolver;
import com.zextras.carbonio.user_management.ldap.LdapDirectory;
import com.zextras.carbonio.user_management.rights.GroupMemberships.Membership;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

@ApplicationScoped
public class GroupMembershipService {

  public record Result(boolean member, boolean direct) {
    static final Result NOT_MEMBER = new Result(false, false);
  }

  private final LdapDirectory directory;

  @Inject
  public GroupMembershipService(LdapDirectory directory) {
    this.directory = directory;
  }

  public Result membership(String accountId, String group) {
    Optional<Entry> account = directory.accountById(accountId.trim());
    if (account.isEmpty()) {
      return Result.NOT_MEMBER;
    }
    String wanted = group.trim();
    String wantedAddress = LdapDirectory.normalizeAddress(wanted);
    String name = AccountResolver.accountName(account.get());
    boolean member = false;
    boolean direct = false;
    for (Membership membership : GroupMemberships.of(directory, account.get(), name)) {
      if (matches(membership.group(), wanted, wantedAddress)) {
        member = true;
        direct |= membership.directStatic();
      }
    }
    return new Result(member, direct);
  }

  private static boolean matches(Entry group, String wanted, String wantedAddress) {
    String id = group.getAttributeValue(ZimbraAttributes.ID);
    if (id != null && id.equalsIgnoreCase(wanted)) {
      return true;
    }
    return Stream.of(
            ZimbraAttributes.MAIL,
            ZimbraAttributes.MAIL_DELIVERY_ADDRESS,
            ZimbraAttributes.MAIL_ALIAS)
        .map(group::getAttributeValues)
        .filter(values -> values != null)
        .flatMap(Arrays::stream)
        .anyMatch(address -> address.equalsIgnoreCase(wantedAddress));
  }
}
