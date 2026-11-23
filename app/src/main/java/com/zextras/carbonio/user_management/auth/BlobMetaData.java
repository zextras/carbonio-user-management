package com.zextras.carbonio.user_management.auth;

import java.util.HashMap;
import java.util.Map;

final class BlobMetaData {

  private BlobMetaData() {}

  static Map<String, String> decode(String metaData) {
    Map<String, String> map = new HashMap<>();
    int offset = 0;
    int length = metaData.length();
    int p;
    while (offset < length && (p = metaData.indexOf('=', offset)) != -1) {
      String name = metaData.substring(offset, p);
      p++;
      int lengthStart = p;
      while (p < length && Character.isDigit(metaData.charAt(p))) {
        p++;
      }
      if (p >= length || p == lengthStart || metaData.charAt(p) != ':') {
        throw new IllegalArgumentException("Bad value length for " + name);
      }
      int valueLength = Integer.parseInt(metaData.substring(lengthStart, p));
      p++;
      if (p + valueLength > length) {
        throw new IllegalArgumentException("Bad value length for " + name);
      }
      String value = metaData.substring(p, p + valueLength);
      p += valueLength;
      if (p >= length || metaData.charAt(p) != ';') {
        throw new IllegalArgumentException("Missing ';' after " + name);
      }
      map.put(name, value);
      offset = p + 1;
    }
    return map;
  }
}
