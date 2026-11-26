package com.zextras.carbonio.user_management.rights;

import java.util.List;
import java.util.Set;

public sealed interface Right {

  String name();

  boolean userRight();

  boolean executableOn(String targetType);

  record Preset(String name, boolean userRight, Set<String> targetTypes) implements Right {
    @Override
    public boolean executableOn(String targetType) {
      return targetTypes.contains(targetType);
    }
  }

  /** {@code attrs} is null for a right that covers every attribute. */
  record Attr(String name, boolean set, Set<String> targetTypes, Set<String> attrs)
      implements Right {
    @Override
    public boolean userRight() {
      return false;
    }

    @Override
    public boolean executableOn(String targetType) {
      return targetTypes.contains(targetType);
    }

    public boolean allAttrs() {
      return attrs == null;
    }

    public boolean suitableFor(boolean setNeeded) {
      return set || !setNeeded;
    }
  }

  record Combo(String name, List<String> members) implements Right {
    @Override
    public boolean userRight() {
      return false;
    }

    @Override
    public boolean executableOn(String targetType) {
      return true;
    }
  }
}
