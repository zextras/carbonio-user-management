package com.zextras.carbonio.user_management.ldap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unboundid.ldap.sdk.FailoverServerSet;
import com.unboundid.ldap.sdk.LDAPConnectionOptions;
import com.unboundid.ldap.sdk.ServerSet;
import com.unboundid.ldap.sdk.SingleServerSet;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LdapServersTest {

  @Test
  void singleUrl() {
    ServerSet set = LdapServers.from("ldap://ldap.example.com:389");

    assertThat(set).isInstanceOf(SingleServerSet.class);
    SingleServerSet single = (SingleServerSet) set;
    assertThat(single.getAddress()).isEqualTo("ldap.example.com");
    assertThat(single.getPort()).isEqualTo(389);
    assertThat(single.getSocketFactory()).isEqualTo(SocketFactory.getDefault());
  }

  @Test
  void defaultPortFromScheme() {
    SingleServerSet set = (SingleServerSet) LdapServers.from("ldap://ldap.example.com");

    assertThat(set.getPort()).isEqualTo(389);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "ldap://a.example.com:389 ldap://b.example.com:1389",
        "ldap://a.example.com:389,ldap://b.example.com:1389",
        "  ldap://a.example.com:389   ldap://b.example.com:1389  "
      })
  void severalUrlsFailOverInOrder(String urls) {
    ServerSet set = LdapServers.from(urls);

    assertThat(set).isInstanceOf(FailoverServerSet.class);
    ServerSet[] servers = ((FailoverServerSet) set).getServerSets();
    assertThat(servers).hasSize(2);
    assertThat(((SingleServerSet) servers[0]).getAddress()).isEqualTo("a.example.com");
    assertThat(((SingleServerSet) servers[1]).getAddress()).isEqualTo("b.example.com");
    assertThat(((SingleServerSet) servers[1]).getPort()).isEqualTo(1389);
  }

  @Test
  void ldapsUsesTls() {
    SingleServerSet set = (SingleServerSet) LdapServers.from("ldaps://ldap.example.com");

    assertThat(set.getPort()).isEqualTo(636);
    assertThat(set.getSocketFactory()).isInstanceOf(SSLSocketFactory.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "   ",
        "http://ldap.example.com",
        "ldap://",
        "not a url",
        "ldap://a.example.com ldaps://b.example.com"
      })
  void rejectsInvalidInput(String urls) {
    assertThatThrownBy(() -> LdapServers.from(urls)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNull() {
    assertThatThrownBy(() -> LdapServers.from(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void detectsLdapsFromTheFirstUrl() {
    assertThat(LdapServers.isLdaps("ldaps://a.example.com:636 ldaps://b.example.com")).isTrue();
    assertThat(LdapServers.isLdaps("ldap://a.example.com,ldap://b.example.com")).isFalse();
  }

  @Test
  void appliesConnectionOptions() {
    LDAPConnectionOptions options = new LDAPConnectionOptions();
    options.setResponseTimeoutMillis(1234);
    SingleServerSet set = (SingleServerSet) LdapServers.from("ldap://a.example.com", options);
    assertThat(set.getConnectionOptions().getResponseTimeoutMillis()).isEqualTo(1234);
  }
}
