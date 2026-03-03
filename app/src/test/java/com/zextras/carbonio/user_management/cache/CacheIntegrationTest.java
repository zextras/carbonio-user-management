// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.cache;

import static com.zextras.carbonio.user_management.UserManagementServiceConfig.FeatureFlags;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zextras.carbonio.user_management.ConsulTestResource;
import com.zextras.carbonio.user_management.PostgresTestResource;
import com.zextras.carbonio.user_management.record.UserInfo;
import com.zextras.carbonio.user_management.record.UserMyself;
import com.zextras.carbonio.user_management.repository.UserInfoCacheRepository;
import com.zextras.carbonio.user_management.repository.UserMyselfCacheRepository;
import com.zextras.carbonio.user_management.repository.UserMyselfCacheRepository.CachedUserMyself;
import com.zextras.carbonio.user_management.repository.UserMyselfCacheRepository.TokenLookupResult;
import com.zextras.carbonio.user_management.service.UserService;
import com.zextras.mailbox.client.MailboxClientException;
import com.zextras.mailbox.client.service.ServiceClient;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@QuarkusTest
@WithTestResource(PostgresTestResource.class)
@WithTestResource(ConsulTestResource.class)
class CacheIntegrationTest {

  @Inject
  UserInfoCacheRepository userInfoRepo;

  @Inject
  UserMyselfCacheRepository userMyselfRepo;

  @Inject
  UserInfoCache userInfoCache;

  @Inject
  UserMyselfCache userMyselfCache;

  @Inject
  UserService userService;

  @Inject
  ServiceClient mailboxClient;

  @BeforeEach
  void setup() {
    org.mockito.Mockito.reset(mailboxClient);
  }

  @AfterEach
  void cleanup() {
    userInfoCache.clearAll();
    userMyselfCache.clearAll();
    QuarkusTransaction.requiringNew().run(() -> {
      userInfoRepo.deleteAll();
      userMyselfRepo.deleteAll();
    });
  }

  private UserInfo sampleUserInfo() {
    return new UserInfo("user-1", "user@example.com", "John Doe", "example.com", "ACTIVE", "INTERNAL");
  }

  private UserMyself sampleMyself() {
    return new UserMyself(
        "user-1", "user@example.com", "John Doe", "example.com",
        "ACTIVE", "INTERNAL", "en", Map.of(FeatureFlags.FILES_ENABLED, true));
  }

  private long futureExpiresAt() {
    return System.currentTimeMillis() + 3_600_000;
  }

  // ---- UserInfoCacheRepository ----

  @Nested
  class UserInfoRepositoryTests {

    @Test
    void insertsNewEntry() {
      UserInfo result = userInfoRepo.upsert(sampleUserInfo(), futureExpiresAt());

      assertThat(result).isEqualTo(sampleUserInfo());
      Optional<UserInfo> found = userInfoRepo.findByUserId("user-1");
      assertThat(found).contains(sampleUserInfo());
    }

    @Test
    void newerUpsertWins() {
      long now = System.currentTimeMillis();
      userInfoRepo.upsert(sampleUserInfo(), now + 10_000);

      UserInfo newer = new UserInfo("user-1", "new@example.com", "New Name",
          "example.com", "ACTIVE", "INTERNAL");
      UserInfo result = userInfoRepo.upsert(newer, now + 20_000);

      assertThat(result).isEqualTo(newer);
    }

    @Test
    void olderUpsertLosesAndReturnsDbValue() {
      long now = System.currentTimeMillis();
      UserInfo first = sampleUserInfo();
      userInfoRepo.upsert(first, now + 20_000);

      UserInfo older = new UserInfo("user-1", "old@example.com", "Old Name",
          "example.com", "ACTIVE", "INTERNAL");
      UserInfo result = userInfoRepo.upsert(older, now + 10_000);

      assertThat(result).isEqualTo(first);
    }

    @Test
    void findByUserIdFiltersExpired() {
      long past = System.currentTimeMillis() - 1000;
      userInfoRepo.upsert(sampleUserInfo(), past);

      Optional<UserInfo> result = userInfoRepo.findByUserId("user-1");
      assertThat(result).isEmpty();
    }

    @Test
    void findByEmailWorks() {
      userInfoRepo.upsert(sampleUserInfo(), futureExpiresAt());

      Optional<UserInfo> result = userInfoRepo.findByEmail("user@example.com");
      assertThat(result).contains(sampleUserInfo());
    }

    @Test
    void findByEmailFiltersExpired() {
      long past = System.currentTimeMillis() - 1000;
      userInfoRepo.upsert(sampleUserInfo(), past);

      Optional<UserInfo> result = userInfoRepo.findByEmail("user@example.com");
      assertThat(result).isEmpty();
    }

    @Test
    void deleteExpiredRemovesOnlyExpired() {
      long now = System.currentTimeMillis();
      userInfoRepo.upsert(sampleUserInfo(), now - 1000);

      UserInfo alive = new UserInfo("user-2", "alive@example.com", "Alive",
          "example.com", "ACTIVE", "INTERNAL");
      userInfoRepo.upsert(alive, now + 3_600_000);

      int deleted = userInfoRepo.deleteExpired();

      assertThat(deleted).isEqualTo(1);
      assertThat(userInfoRepo.findByUserId("user-1")).isEmpty();
      assertThat(userInfoRepo.findByUserId("user-2")).contains(alive);
    }
  }

  // ---- UserMyselfCacheRepository ----

  @Nested
  class UserMyselfRepositoryTests {

    @Test
    void insertsNewEntry() {
      UserMyself result = userMyselfRepo.upsert("user-1", "token-abc", sampleMyself(), futureExpiresAt());

      assertThat(result).isEqualTo(sampleMyself());
    }

    @Test
    void findByUserIdReturnsDetailsWithExpiresAt() {
      long expiresAt = futureExpiresAt();
      userMyselfRepo.upsert("user-1", "token-abc", sampleMyself(), expiresAt);

      Optional<CachedUserMyself> result = userMyselfRepo.findByUserId("user-1");

      assertThat(result).isPresent();
      assertThat(result.get().myself()).isEqualTo(sampleMyself());
      assertThat(result.get().expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void findByTokenReturnsUserIdAndMyself() {
      long expiresAt = futureExpiresAt();
      userMyselfRepo.upsert("user-1", "token-abc", sampleMyself(), expiresAt);

      Optional<TokenLookupResult> result = userMyselfRepo.findByToken("token-abc");

      assertThat(result).isPresent();
      assertThat(result.get().userId()).isEqualTo("user-1");
      assertThat(result.get().myself()).isEqualTo(sampleMyself());
      assertThat(result.get().expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void jsonbFeatureListSerialization() {
      Map<String, Boolean> features = Map.of(
          FeatureFlags.FILES_ENABLED, true,
          FeatureFlags.WSC_ENABLED, false,
          FeatureFlags.TASKS_ENABLED, true);
      UserMyself myself = new UserMyself(
          "user-1", "user@example.com", "John Doe", "example.com",
          "ACTIVE", "INTERNAL", "it", features);
      userMyselfRepo.upsert("user-1", "token-abc", myself, futureExpiresAt());

      Optional<CachedUserMyself> result = userMyselfRepo.findByUserId("user-1");

      assertThat(result).isPresent();
      assertThat(result.get().myself().locale()).isEqualTo("it");
      assertThat(result.get().myself().featureList()).isEqualTo(features);
    }

    @Test
    void newerUpsertWins() {
      long now = System.currentTimeMillis();
      userMyselfRepo.upsert("user-1", "token-1", sampleMyself(), now + 10_000);

      UserMyself newer = new UserMyself(
          "user-1", "new@example.com", "New Name", "example.com",
          "ACTIVE", "INTERNAL", "it", Map.of());
      UserMyself result = userMyselfRepo.upsert("user-1", "token-2", newer, now + 20_000);

      assertThat(result).isEqualTo(newer);
    }

    @Test
    void olderUpsertLosesAndReturnsDbValue() {
      long now = System.currentTimeMillis();
      UserMyself first = sampleMyself();
      userMyselfRepo.upsert("user-1", "token-1", first, now + 20_000);

      UserMyself older = new UserMyself(
          "user-1", "old@example.com", "Old Name", "example.com",
          "ACTIVE", "INTERNAL", "it", Map.of());
      UserMyself result = userMyselfRepo.upsert("user-1", "token-2", older, now + 10_000);

      assertThat(result).isEqualTo(first);
    }

    @Test
    void deleteExpiredRemovesOnlyExpired() {
      long now = System.currentTimeMillis();
      userMyselfRepo.upsert("user-1", "token-1", sampleMyself(), now - 1000);
      userMyselfRepo.upsert("user-2", "token-2", new UserMyself(
          "user-2", "u2@x.com", "U2", "x.com", "ACTIVE", "INTERNAL", "en", Map.of()),
          now + 3_600_000);

      int deleted = userMyselfRepo.deleteExpired();

      assertThat(deleted).isEqualTo(1);
      assertThat(userMyselfRepo.findByUserId("user-1")).isEmpty();
      assertThat(userMyselfRepo.findByUserId("user-2")).isPresent();
    }
  }

  // ---- Full flow: L2 -> L1 -> SOAP ----

  @Nested
  class FullFlowTests {

    @Test
    void getUserById_L2Hit_noDbNoSoap() {
      UserInfo info = sampleUserInfo();
      userInfoCache.put(info);

      Optional<UserInfo> result = userService.getUserById("user-1", "token-1");

      assertThat(result).contains(info);
      verify(mailboxClient, never()).send(any());
    }

    @Test
    void getUserById_L2Miss_L1Hit_populatesCaffeine() {
      userInfoRepo.upsert(sampleUserInfo(), futureExpiresAt());

      Optional<UserInfo> result = userService.getUserById("user-1", "token-1");

      assertThat(result).contains(sampleUserInfo());
      assertThat(userInfoCache.getByUserId("user-1")).contains(sampleUserInfo());
      verify(mailboxClient, never()).send(any());
    }

    @Test
    void getUserById_L2Miss_L1Miss_callsSoap() throws Exception {
      when(mailboxClient.send(any())).thenThrow(new MailboxClientException("test"));

      Optional<UserInfo> result = userService.getUserById("user-1", "token-1");

      assertThat(result).isEmpty();
      verify(mailboxClient).send(any());
    }

    @Test
    void getUserByEmail_L2Miss_L1Hit_populatesCaffeine() {
      userInfoRepo.upsert(sampleUserInfo(), futureExpiresAt());

      Optional<UserInfo> result = userService.getUserByEmail("user@example.com", "token-1");

      assertThat(result).contains(sampleUserInfo());
      assertThat(userInfoCache.getByEmail("user@example.com")).contains(sampleUserInfo());
      verify(mailboxClient, never()).send(any());
    }

    @Test
    void getUserMyself_L2Miss_L1Hit_resolvesFromDb() {
      // Pre-populate only user_myself_cache (no user_info_cache)
      userMyselfRepo.upsert("user-1", "token-1", sampleMyself(), futureExpiresAt());

      Optional<UserMyself> result = userService.getUserMyself("token-1");

      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(sampleMyself());
      // Caffeine should now be populated
      assertThat(userMyselfCache.getByToken("token-1")).contains(sampleMyself());
      assertThat(userMyselfCache.resolveUserId("token-1")).contains("user-1");
      verify(mailboxClient, never()).send(any());
    }

    @Test
    void getUserMyself_L2Miss_L1Miss_callsSoap() throws Exception {
      when(mailboxClient.send(any())).thenThrow(new MailboxClientException("test"));

      Optional<UserMyself> result = userService.getUserMyself("token-1");

      assertThat(result).isEmpty();
      verify(mailboxClient).send(any());
    }

    @Test
    void getUserMyself_doesNotWriteToUserInfoCache() {
      // Pre-populate user_myself_cache
      userMyselfRepo.upsert("user-1", "token-1", sampleMyself(), futureExpiresAt());

      userService.getUserMyself("token-1");

      // user_info_cache should NOT be populated by getUserMyself
      assertThat(userInfoCache.getByUserId("user-1")).isEmpty();
    }
  }
}
