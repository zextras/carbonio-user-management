package com.zextras.carbonio.user_management.rights;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * The rights catalog mailbox loads at runtime: the {@code /conf/rights/} and {@code
 * /conf/attrs/attrs.xml} resources of {@code com.zextras:mailbox-attribute-manager}.
 */
@ApplicationScoped
public class RightsCatalog {

  static final String RIGHTS_PATH = "/conf/rights/";
  static final String ATTRS_RESOURCE = "/conf/attrs/attrs.xml";
  static final List<String> RIGHTS_FILES =
      List.of(
          "rights.xml",
          "rights-roles.xml",
          "user-rights.xml",
          "rights-adminconsole.xml",
          "rights-adminconsole-domainadmin.xml",
          "rights-domainadmin.xml");
  static final Set<String> TARGET_TYPES =
      Set.of(
          "account",
          "calresource",
          "cos",
          "dl",
          "group",
          "domain",
          "server",
          "xmppcomponent",
          "zimlet",
          "config",
          "global");

  private final Map<String, Right> rights;
  private final Map<String, String> descriptions;
  private final Set<String> attributes;
  private final Map<String, Set<Right.Preset>> comboPresets = new HashMap<>();
  private final Map<String, List<Right.Attr>> comboAttrs = new HashMap<>();

  public RightsCatalog() {
    this(loadRights(), loadAttributes());
  }

  RightsCatalog(List<Parsed> parsed, Set<String> attributes) {
    Map<String, Right> byName = new HashMap<>();
    Map<String, String> descriptionByName = new HashMap<>();
    for (Parsed right : parsed) {
      byName.put(right.right().name(), right.right());
      if (right.description() != null) {
        descriptionByName.put(right.right().name(), right.description());
      }
    }
    this.rights = Map.copyOf(byName);
    this.descriptions = Map.copyOf(descriptionByName);
    this.attributes = Set.copyOf(attributes);
    for (Right right : this.rights.values()) {
      if (right instanceof Right.Combo combo) {
        expand(combo);
      }
    }
  }

  public Optional<Right> find(String name) {
    if (name == null) {
      return Optional.empty();
    }
    if (name.indexOf('.') >= 0) {
      return inline(name);
    }
    return Optional.ofNullable(rights.get(name));
  }

  public List<Right> checkableOn(String targetType) {
    return rights.values().stream()
        .filter(right -> !(right instanceof Right.Combo))
        .filter(right -> right.executableOn(targetType))
        .sorted(Comparator.comparing(Right::name))
        .toList();
  }

  public String description(Right right) {
    return descriptions.get(right.name());
  }

  public boolean isTargetType(String code) {
    return TARGET_TYPES.contains(code);
  }

  Set<Right.Preset> presetRights(Right.Combo combo) {
    return comboPresets.get(combo.name());
  }

  List<Right.Attr> attrRights(Right.Combo combo) {
    return comboAttrs.get(combo.name());
  }

  private Optional<Right> inline(String name) {
    String[] parts = name.split("\\.");
    if (parts.length != 3) {
      return Optional.empty();
    }
    boolean set;
    if ("set".equals(parts[0])) {
      set = true;
    } else if ("get".equals(parts[0])) {
      set = false;
    } else {
      return Optional.empty();
    }
    if (!TARGET_TYPES.contains(parts[1]) || "global".equals(parts[1])) {
      return Optional.empty();
    }
    if (!attributes.contains(parts[2].toLowerCase(Locale.ROOT))) {
      return Optional.empty();
    }
    return Optional.of(
        new Right.Attr(name, set, Set.of(parts[1]), Set.of(parts[2].toLowerCase(Locale.ROOT))));
  }

  private void expand(Right.Combo root) {
    Set<Right.Preset> presets = new LinkedHashSet<>();
    List<Right.Attr> attrs = new ArrayList<>();
    Set<String> visited = new HashSet<>();
    Deque<Right.Combo> pending = new ArrayDeque<>();
    pending.push(root);
    while (!pending.isEmpty()) {
      Right.Combo combo = pending.pop();
      if (!visited.add(combo.name())) {
        continue;
      }
      for (String member : combo.members()) {
        Optional<Right> right = find(member);
        if (right.isEmpty()) {
          continue;
        }
        switch (right.get()) {
          case Right.Preset preset -> presets.add(preset);
          case Right.Attr attr -> attrs.add(attr);
          case Right.Combo nested -> pending.push(nested);
        }
      }
    }
    comboPresets.put(root.name(), Set.copyOf(presets));
    comboAttrs.put(root.name(), List.copyOf(attrs));
  }

  record Parsed(Right right, String description) {}

  static List<Parsed> loadRights() {
    List<Parsed> rights = new ArrayList<>();
    for (String file : RIGHTS_FILES) {
      try (InputStream in = RightsCatalog.class.getResourceAsStream(RIGHTS_PATH + file)) {
        if (in == null) {
          throw new IllegalStateException("Rights resource not found: " + RIGHTS_PATH + file);
        }
        rights.addAll(parseRights(in));
      } catch (IOException | XMLStreamException e) {
        throw new IllegalStateException("Cannot load rights from " + file, e);
      }
    }
    return rights;
  }

  static List<Parsed> parseRights(InputStream in) throws XMLStreamException {
    XMLStreamReader reader = xmlInputFactory().createXMLStreamReader(in);
    List<Parsed> parsed = new ArrayList<>();
    String description = null;
    String name = null;
    String type = null;
    boolean userRight = false;
    String targetType = null;
    Set<String> attrs = null;
    List<String> members = null;
    int depth = 0;
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        depth++;
        String element = reader.getLocalName();
        if (depth == 2 && "right".equals(element)) {
          name = reader.getAttributeValue(null, "name");
          type = reader.getAttributeValue(null, "type");
          userRight = "1".equals(reader.getAttributeValue(null, "userRight"));
          targetType = reader.getAttributeValue(null, "targetType");
          attrs = null;
          description = null;
          members = new ArrayList<>();
        } else if (depth == 3 && "desc".equals(element) && name != null) {
          description = reader.getElementText().trim();
          depth--;
        } else if (depth == 3 && "attrs".equals(element)) {
          attrs = new HashSet<>();
        } else if (depth == 4 && "a".equals(element) && attrs != null) {
          attrs.add(reader.getAttributeValue(null, "n").toLowerCase(Locale.ROOT));
        } else if (depth == 4 && "r".equals(element) && members != null) {
          members.add(reader.getAttributeValue(null, "n"));
        }
      } else if (event == XMLStreamConstants.END_ELEMENT) {
        if (depth == 2 && "right".equals(reader.getLocalName()) && name != null) {
          parsed.add(
              new Parsed(toRight(name, type, userRight, targetType, attrs, members), description));
          name = null;
        }
        depth--;
      }
    }
    return parsed;
  }

  private static Right toRight(
      String name,
      String type,
      boolean userRight,
      String targetType,
      Set<String> attrs,
      List<String> members) {
    Set<String> targetTypes =
        targetType == null
            ? (userRight ? Set.of("account") : Set.of())
            : Arrays.stream(targetType.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    if (userRight) {
      return new Right.Preset(name, true, targetTypes);
    }
    return switch (type) {
      case "preset" -> new Right.Preset(name, false, targetTypes);
      case "getAttrs" -> new Right.Attr(name, false, targetTypes, attrs == null ? null : Set.copyOf(attrs));
      case "setAttrs" -> new Right.Attr(name, true, targetTypes, attrs == null ? null : Set.copyOf(attrs));
      case "combo" -> new Right.Combo(name, List.copyOf(members));
      default -> throw new IllegalStateException("Unknown right type " + type + " for " + name);
    };
  }

  static Set<String> loadAttributes() {
    try (InputStream in = RightsCatalog.class.getResourceAsStream(ATTRS_RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("Attributes resource not found: " + ATTRS_RESOURCE);
      }
      XMLStreamReader reader = xmlInputFactory().createXMLStreamReader(in);
      Set<String> names = new HashSet<>();
      while (reader.hasNext()) {
        if (reader.next() == XMLStreamConstants.START_ELEMENT
            && "attr".equals(reader.getLocalName())) {
          names.add(reader.getAttributeValue(null, "name").toLowerCase(Locale.ROOT));
        }
      }
      return names;
    } catch (IOException | XMLStreamException e) {
      throw new IllegalStateException("Cannot load " + ATTRS_RESOURCE, e);
    }
  }

  private static XMLInputFactory xmlInputFactory() {
    XMLInputFactory factory = XMLInputFactory.newFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    return factory;
  }
}
