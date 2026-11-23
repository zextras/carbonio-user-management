package com.zextras.carbonio.user_management.rest.dto;

public record RightCheckRequest(Subject subject, String right, Target target) {

  public record Subject(String token, String accountId) {}

  public record Target(String type, String id, String name) {}
}
