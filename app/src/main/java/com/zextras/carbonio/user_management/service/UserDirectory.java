package com.zextras.carbonio.user_management.service;

import com.zextras.carbonio.user_management.record.UserInfo;
import com.zextras.carbonio.user_management.record.UserMyself;
import java.util.List;
import java.util.Optional;

public interface UserDirectory {

  Optional<UserInfo> byId(String userId);

  Optional<UserInfo> byEmail(String email);

  List<UserInfo> byIds(List<String> userIds);

  Optional<AuthenticatedUser> byToken(String token);

  record AuthenticatedUser(UserMyself myself, long sessionLifetimeMs) {}
}
