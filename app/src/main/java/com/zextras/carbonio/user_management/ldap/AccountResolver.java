package com.zextras.carbonio.user_management.ldap;

import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.RDN;
import com.zextras.carbonio.user_management.UserManagementServiceConfig.ZimbraAttributes;
import com.zextras.carbonio.user_management.UserManagementServiceConfig.ZimbraObjectClasses;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class AccountResolver {

  private final LdapDirectory directory;

  @Inject
  public AccountResolver(LdapDirectory directory) {
    this.directory = directory;
  }

  public ResolvedAccount resolve(Entry account) {
    String name = accountName(account);
    int at = name.lastIndexOf('@');
    Optional<Entry> domain =
        at < 0 ? Optional.empty() : directory.domainByName(name.substring(at + 1));
    boolean external = ResolvedAccount.isTrue(account, ZimbraAttributes.IS_EXTERNAL_VIRTUAL_ACCOUNT);
    return new ResolvedAccount(account, name, domain, cos(account, domain, external), directory.config());
  }

  private Optional<Entry> cos(Entry account, Optional<Entry> domain, boolean external) {
    Optional<Entry> cos =
        Optional.ofNullable(account.getAttributeValue(ZimbraAttributes.COS_ID))
            .flatMap(directory::cosById);
    if (cos.isPresent()) {
      return cos;
    }
    cos =
        domain
            .map(
                d ->
                    d.getAttributeValue(
                        external
                            ? ZimbraAttributes.DOMAIN_DEFAULT_EXTERNAL_COS_ID
                            : ZimbraAttributes.DOMAIN_DEFAULT_COS_ID))
            .flatMap(directory::cosById);
    if (cos.isPresent()) {
      return cos;
    }
    return directory.cosByName(
        external
            ? ZimbraObjectClasses.DEFAULT_EXTERNAL_COS_NAME
            : ZimbraObjectClasses.DEFAULT_COS_NAME);
  }

  public static String accountName(Entry account) {
    String deliveryAddress = account.getAttributeValue(ZimbraAttributes.MAIL_DELIVERY_ADDRESS);
    if (deliveryAddress != null) {
      return deliveryAddress;
    }
    return nameFromDn(account.getDN());
  }

  private static String nameFromDn(String dn) {
    try {
      String localPart = null;
      List<String> domainParts = new ArrayList<>();
      for (RDN rdn : new com.unboundid.ldap.sdk.DN(dn).getRDNs()) {
        String attribute = rdn.getAttributeNames()[0];
        String value = rdn.getAttributeValues()[0];
        if (localPart == null && ("uid".equalsIgnoreCase(attribute) || "cn".equalsIgnoreCase(attribute))) {
          localPart = value;
        } else if ("dc".equalsIgnoreCase(attribute)) {
          domainParts.add(value);
        }
      }
      if (localPart == null || domainParts.isEmpty()) {
        return dn;
      }
      return localPart + "@" + String.join(".", domainParts);
    } catch (com.unboundid.ldap.sdk.LDAPException e) {
      return dn;
    }
  }
}
