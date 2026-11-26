package com.zextras.carbonio.user_management.rest.dto;

import com.zextras.carbonio.user_management.rights.Right;

public record RightInfoDto(String name, String type, boolean userRight, String description) {

  public static RightInfoDto from(Right right, String description) {
    String type =
        switch (right) {
          case Right.Preset ignored -> "preset";
          case Right.Attr attr -> attr.set() ? "setAttrs" : "getAttrs";
          case Right.Combo ignored -> "combo";
        };
    return new RightInfoDto(right.name(), type, right.userRight(), description);
  }
}
