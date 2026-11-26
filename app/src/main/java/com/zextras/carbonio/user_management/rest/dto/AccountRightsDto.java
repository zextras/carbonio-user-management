package com.zextras.carbonio.user_management.rest.dto;

import com.zextras.carbonio.user_management.rights.RightsService.AccountRights;

public record AccountRightsDto(
    String actorId,
    String targetId,
    boolean isGlobalAdmin,
    boolean isDelegatedAdmin,
    boolean canWrite,
    boolean canConfigure) {

  public static AccountRightsDto from(AccountRights rights) {
    return new AccountRightsDto(
        rights.actorId(),
        rights.targetId(),
        rights.isGlobalAdmin(),
        rights.isDelegatedAdmin(),
        rights.canWrite(),
        rights.canConfigure());
  }
}
