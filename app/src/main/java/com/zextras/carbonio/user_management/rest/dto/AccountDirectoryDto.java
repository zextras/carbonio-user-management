package com.zextras.carbonio.user_management.rest.dto;

import com.zextras.carbonio.user_management.record.AccountDirectory;

public record AccountDirectoryDto(String accountId, String cosId, String domainId) {

  public static AccountDirectoryDto from(AccountDirectory directory) {
    return new AccountDirectoryDto(directory.accountId(), directory.cosId(), directory.domainId());
  }
}
