package com.zextras.carbonio.user_management.ldap;

import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.Entry;
import com.zextras.carbonio.user_management.UserManagementServiceConfig.ZimbraAttributes;
import com.zextras.carbonio.user_management.record.UserInfo;
import com.zextras.carbonio.user_management.record.UserMyself;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public final class UserMapper {

  static final String FEATURE_PREFIX = "carbonioFeature";
  static final List<String> CAPABILITY_PREFIXES =
      List.of("carbonioWsc", "carbonioFiles", "carbonioTasks", "carbonioDocs", "carbonioPreview");
  private static final Set<String> ACCOUNT_STATUSES =
      Set.of("active", "maintenance", "locked", "closed", "lockout", "pending");
  private static final String ATTRS_RESOURCE = "/conf/attrs/attrs.xml";
  static final List<String> SETTING_ATTRIBUTES = settingAttributes();
  private static final Map<String, String> SETTING_NAMES = canonicalNames(SETTING_ATTRIBUTES);

  private UserMapper() {}

  public static UserInfo toUserInfo(ResolvedAccount account) {
    return new UserInfo(
        account.id(),
        account.name(),
        Optional.ofNullable(account.account().getAttributeValue(ZimbraAttributes.DISPLAY_NAME))
            .orElse(""),
        Optional.ofNullable(publicServiceUrl(account)).orElse(""),
        status(account.account()),
        account.isExternalVirtual() ? "GUEST" : "INTERNAL",
        account.cosId(),
        account.domainId().orElse(null),
        account.isGlobalAdmin());
  }

  public static UserMyself toUserMyself(
      ResolvedAccount account, Function<String, Optional<Entry>> servers) {
    UserInfo info = toUserInfo(account);
    Map<String, String> inherited = accountInherited(account);
    List<String> features =
        inherited.entrySet().stream()
            .filter(e -> e.getKey().startsWith(FEATURE_PREFIX))
            .filter(e -> Boolean.parseBoolean(e.getValue()))
            .map(Map.Entry::getKey)
            .toList();
    Map<String, String> capabilities = new TreeMap<>();
    inherited.forEach(
        (key, value) -> {
          if (CAPABILITY_PREFIXES.stream().anyMatch(key::startsWith)) {
            capabilities.put(key, value);
          }
        });
    return new UserMyself(
        info.userId(),
        info.email(),
        info.fullName(),
        info.domain(),
        info.status(),
        info.type(),
        info.cosId(),
        info.domainId(),
        info.isGlobalAdmin(),
        locale(account, servers),
        features,
        capabilities);
  }

  static String publicServiceUrl(ResolvedAccount account) {
    Optional<Entry> domain = account.domain();
    if (domain.isPresent()) {
      String hostname =
          domainInherited(domain.get(), account, ZimbraAttributes.PUBLIC_SERVICE_HOSTNAME);
      if (hostname != null && !hostname.isBlank()) {
        String protocol =
            Optional.ofNullable(
                    domainInherited(
                        domain.get(), account, ZimbraAttributes.PUBLIC_SERVICE_PROTOCOL))
                .orElse("http");
        return protocol + "://" + hostname;
      }
    }
    return account.domainName();
  }

  static String status(Entry account) {
    String status = account.getAttributeValue(ZimbraAttributes.ACCOUNT_STATUS);
    if (status == null || !ACCOUNT_STATUSES.contains(status)) {
      return "ACTIVE";
    }
    return status.toUpperCase(Locale.ROOT);
  }

  static String locale(ResolvedAccount account, Function<String, Optional<Entry>> servers) {
    String value =
        firstNonNull(
            account.accountOrCos(ZimbraAttributes.PREF_LOCALE),
            account.accountOrCos(ZimbraAttributes.LOCALE),
            account.domain().map(d -> d.getAttributeValue(ZimbraAttributes.LOCALE)).orElse(null),
            Optional.ofNullable(account.account().getAttributeValue(ZimbraAttributes.MAIL_HOST))
                .flatMap(servers)
                .map(s -> s.getAttributeValue(ZimbraAttributes.LOCALE))
                .orElse(null),
            account.config().getAttributeValue(ZimbraAttributes.LOCALE));
    if (value == null) {
      return Locale.ENGLISH.toString();
    }
    return toLocale(value).toString();
  }

  static Locale toLocale(String name) {
    String[] parts = name.indexOf('_') != -1 ? name.split("_") : name.split("-");
    return switch (parts.length) {
      case 1 -> Locale.of(parts[0]);
      case 2 -> Locale.of(parts[0], parts[1]);
      default -> Locale.of(parts[0], parts[1], parts[2]);
    };
  }

  private static Map<String, String> accountInherited(ResolvedAccount account) {
    Map<String, String> values = new TreeMap<>();
    account.cos().ifPresent(cos -> putSingleValued(cos, values));
    putSingleValued(account.account(), values);
    return values;
  }

  private static void putSingleValued(Entry entry, Map<String, String> values) {
    for (Attribute attribute : entry.getAttributes()) {
      String name = attribute.getName();
      if (isSetting(name)) {
        values.put(SETTING_NAMES.getOrDefault(name, name), attribute.getValue());
      }
    }
  }

  private static boolean isSetting(String name) {
    return name.startsWith(FEATURE_PREFIX)
        || CAPABILITY_PREFIXES.stream().anyMatch(name::startsWith);
  }

  private static Map<String, String> canonicalNames(List<String> names) {
    Map<String, String> canonical = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    names.forEach(name -> canonical.put(name, name));
    return Collections.unmodifiableMap(canonical);
  }

  static List<String> settingAttributes() {
    try (InputStream in = UserMapper.class.getResourceAsStream(ATTRS_RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("Attributes resource not found: " + ATTRS_RESOURCE);
      }
      XMLInputFactory factory = XMLInputFactory.newFactory();
      factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
      factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
      XMLStreamReader reader = factory.createXMLStreamReader(in);
      List<String> names = new ArrayList<>();
      while (reader.hasNext()) {
        if (reader.next() == XMLStreamConstants.START_ELEMENT
            && "attr".equals(reader.getLocalName())) {
          String name = reader.getAttributeValue(null, "name");
          if (name != null && isSetting(name)) {
            names.add(name);
          }
        }
      }
      return List.copyOf(names);
    } catch (IOException | XMLStreamException e) {
      throw new IllegalStateException("Cannot load " + ATTRS_RESOURCE, e);
    }
  }

  private static String domainInherited(Entry domain, ResolvedAccount account, String attribute) {
    String own = domain.getAttributeValue(attribute);
    return own != null ? own : account.config().getAttributeValue(attribute);
  }

  @SafeVarargs
  private static <T> T firstNonNull(T... values) {
    for (T value : values) {
      if (value != null) {
        return value;
      }
    }
    return null;
  }
}
