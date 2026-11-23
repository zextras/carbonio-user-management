package com.zextras.carbonio.user_management.rights;

import com.unboundid.ldap.sdk.Entry;
import com.zextras.carbonio.user_management.UserManagementServiceConfig.ZimbraAttributes;
import com.zextras.carbonio.user_management.ldap.LdapDirectory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class GroupMemberships {

  record Membership(Entry group, boolean directStatic) {
    String id() {
      return group.getAttributeValue(ZimbraAttributes.ID);
    }

    boolean adminGroup() {
      return "TRUE".equalsIgnoreCase(group.getAttributeValue(ZimbraAttributes.IS_ADMIN_GROUP));
    }
  }

  private GroupMemberships() {}

  static List<Membership> of(LdapDirectory directory, Entry account, String accountName) {
    List<Membership> memberships = new ArrayList<>();
    Set<String> seen = new HashSet<>();

    Set<String> addresses = new LinkedHashSet<>();
    addresses.add(accountName);
    addAll(addresses, account.getAttributeValues(ZimbraAttributes.MAIL_ALIAS));
    boolean direct = true;
    while (!addresses.isEmpty()) {
      Set<String> next = new LinkedHashSet<>();
      for (Entry dl : directory.distributionListsWithMember(addresses)) {
        Membership membership = new Membership(dl, direct);
        if (membership.id() != null && seen.add(membership.id())) {
          memberships.add(membership);
          addAll(next, dl.getAttributeValues(ZimbraAttributes.MAIL_DELIVERY_ADDRESS));
          addAll(next, dl.getAttributeValues(ZimbraAttributes.MAIL));
          addAll(next, dl.getAttributeValues(ZimbraAttributes.MAIL_ALIAS));
        }
      }
      addresses = next;
      direct = false;
    }

    String[] dynamicIds = account.getAttributeValues(ZimbraAttributes.MEMBER_OF);
    if (dynamicIds != null) {
      for (Entry group : directory.dynamicGroupsByIds(Arrays.asList(dynamicIds))) {
        Membership membership = new Membership(group, false);
        if (membership.id() != null && seen.add(membership.id())) {
          memberships.add(membership);
        }
      }
    }
    return memberships;
  }

  private static void addAll(Set<String> target, String[] values) {
    if (values != null) {
      target.addAll(Arrays.asList(values));
    }
  }
}
