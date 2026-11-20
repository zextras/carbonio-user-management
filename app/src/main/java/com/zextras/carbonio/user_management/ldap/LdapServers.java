package com.zextras.carbonio.user_management.ldap;

import com.unboundid.ldap.sdk.FailoverServerSet;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPURL;
import com.unboundid.ldap.sdk.ServerSet;
import com.unboundid.ldap.sdk.SingleServerSet;
import com.unboundid.util.ssl.JVMDefaultTrustManager;
import com.unboundid.util.ssl.SSLUtil;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;
import javax.net.SocketFactory;

public final class LdapServers {

  private static final String LDAP = "ldap";
  private static final String LDAPS = "ldaps";

  private LdapServers() {}

  public static ServerSet from(String urls) {
    if (urls == null || urls.isBlank()) {
      throw new IllegalArgumentException("No LDAP URL configured");
    }
    List<LDAPURL> parsed = Arrays.stream(urls.trim().split("[\\s,]+"))
        .map(LdapServers::parse)
        .toList();

    String scheme = parsed.getFirst().getScheme();
    if (parsed.stream().anyMatch(url -> !url.getScheme().equals(scheme))) {
      throw new IllegalArgumentException("LDAP URLs mix ldap and ldaps: " + urls);
    }

    SocketFactory socketFactory = socketFactory(scheme);
    String[] hosts = parsed.stream().map(LDAPURL::getHost).toArray(String[]::new);
    int[] ports = parsed.stream().mapToInt(LDAPURL::getPort).toArray();

    if (hosts.length == 1) {
      return new SingleServerSet(hosts[0], ports[0], socketFactory);
    }
    return new FailoverServerSet(hosts, ports, socketFactory);
  }

  private static LDAPURL parse(String url) {
    try {
      LDAPURL parsed = new LDAPURL(url);
      if (!LDAP.equals(parsed.getScheme()) && !LDAPS.equals(parsed.getScheme())) {
        throw new IllegalArgumentException("Unsupported LDAP URL scheme: " + url);
      }
      if (!parsed.hostProvided()) {
        throw new IllegalArgumentException("LDAP URL without host: " + url);
      }
      return parsed;
    } catch (LDAPException e) {
      throw new IllegalArgumentException("Invalid LDAP URL: " + url, e);
    }
  }

  private static SocketFactory socketFactory(String scheme) {
    if (!LDAPS.equals(scheme)) {
      return SocketFactory.getDefault();
    }
    try {
      return new SSLUtil(JVMDefaultTrustManager.getInstance()).createSSLSocketFactory();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Cannot create the LDAPS socket factory", e);
    }
  }
}
