package com.zextras.carbonio.user_management.ldap;

import com.unboundid.ldap.sdk.Entry;
import com.zextras.carbonio.user_management.UserManagementServiceConfig.ZimbraAttributes;
import java.util.Optional;

public record ResolvedAccount(
    Entry account, String name, Optional<Entry> domain, Optional<Entry> cos, Entry config) {

  public String id() {
    return account.getAttributeValue(ZimbraAttributes.ID);
  }

  public String domainName() {
    int at = name.lastIndexOf('@');
    return at < 0 ? null : name.substring(at + 1);
  }

  public Optional<String> domainId() {
    return domain.map(d -> d.getAttributeValue(ZimbraAttributes.ID));
  }

  public String cosId() {
    String explicit = account.getAttributeValue(ZimbraAttributes.COS_ID);
    if (explicit != null) {
      return explicit;
    }
    return cos.map(c -> c.getAttributeValue(ZimbraAttributes.ID)).orElse(null);
  }

  public boolean isExternalVirtual() {
    return isTrue(account, ZimbraAttributes.IS_EXTERNAL_VIRTUAL_ACCOUNT);
  }

  public boolean isGlobalAdmin() {
    return isTrue(account, ZimbraAttributes.IS_ADMIN_ACCOUNT);
  }

  public boolean isDelegatedAdmin() {
    return isTrue(account, ZimbraAttributes.IS_DELEGATED_ADMIN_ACCOUNT);
  }

  public String accountOrCos(String attribute) {
    String own = account.getAttributeValue(attribute);
    if (own != null) {
      return own;
    }
    return cos.map(c -> c.getAttributeValue(attribute)).orElse(null);
  }

  static boolean isTrue(Entry entry, String attribute) {
    return "TRUE".equalsIgnoreCase(entry.getAttributeValue(attribute));
  }
}
