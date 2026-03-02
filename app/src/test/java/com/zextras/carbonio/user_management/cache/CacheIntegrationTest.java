// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zextras.carbonio.user_management.ConsulTestResource;
import com.zextras.carbonio.user_management.PostgresTestResource;
import com.zextras.carbonio.user_management.cache.record.UserDetails;
import com.zextras.carbonio.user_management.cache.record.UserInfo;
import com.zextras.carbonio.user_management.cache.repository.UserDetailsCacheRepository;
import com.zextras.carbonio.user_management.cache.repository.UserDetailsCacheRepository.CachedUserDetails;
import com.zextras.carbonio.user_management.cache.repository.UserDetailsCacheRepository.TokenLookupResult;
import com.zextras.carbonio.user_management.cache.repository.UserInfoCacheRepository;
import com.zextras.carbonio.user_management.service.UserService;
import com.zextras.carbonio.user_management.service.UserService.MyselfResult;
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
  UserDetailsCacheRepository userDetailsRepo;

  @Inject
  UserInfoCache userInfoCache;

  @Inject
  UserDetailsCache userDetailsCache;

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
    userDetailsCache.clearAll();
    QuarkusTransaction.requiringNew().run(() -> {
      userInfoRepo.deleteAll();
      userDetailsRepo.deleteAll();
    });
  }

  private UserInfo sampleUserInfo() {
    return new UserInfo("user-1", "user@example.com", "John Doe", "example.com", "ACTIVE", "INTERNAL");
  }

  private UserDetails sampleDetails() {
    return new UserDetails("en", Map.of("carbonioFeatureX", "true"));
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

  // ---- UserDetailsCacheRepository ----

  @Nested
  class UserDetailsRepositoryTests {

    @Test
    void insertsNewEntry() {
      UserDetails result = userDetailsRepo.upsert("user-1", "token-abc", sampleDetails(), futureExpiresAt());

      assertThat(result).isEqualTo(sampleDetails());
    }

    @Test
    void findByUserIdReturnsDetailsWithExpiresAt() {
      long expiresAt = futureExpiresAt();
      userDetailsRepo.upsert("user-1", "token-abc", sampleDetails(), expiresAt);

      Optional<CachedUserDetails> result = userDetailsRepo.findByUserId("user-1");

      assertThat(result).isPresent();
      assertThat(result.get().details()).isEqualTo(sampleDetails());
      assertThat(result.get().expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void findByTokenReturnsUserIdAndDetails() {
      long expiresAt = futureExpiresAt();
      userDetailsRepo.upsert("user-1", "token-abc", sampleDetails(), expiresAt);

      Optional<TokenLookupResult> result = userDetailsRepo.findByToken("token-abc");

      assertThat(result).isPresent();
      assertThat(result.get().userId()).isEqualTo("user-1");
      assertThat(result.get().details()).isEqualTo(sampleDetails());
      assertThat(result.get().expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void resolveUserIdFromToken() {
      userDetailsRepo.upsert("user-1", "token-abc", sampleDetails(), futureExpiresAt());

      Optional<String> result = userDetailsRepo.resolveUserId("token-abc");
      assertThat(result).contains("user-1");
    }

    @Test
    void resolveUserIdReturnsEmptyForExpired() {
      long past = System.currentTimeMillis() - 1000;
      userDetailsRepo.upsert("user-1", "token-abc", sampleDetails(), past);

      Optional<String> result = userDetailsRepo.resolveUserId("token-abc");
      assertThat(result).isEmpty();
    }

    @Test
    void jsonbAttributesSerialization() {
      Map<String, String> attrs = Map.of(
          "carbonioFeatureA", "enabled",
          "carbonioFeatureB", "disabled",
          "carbonioQuota", "1024");
      UserDetails details = new UserDetails("it", attrs);
      userDetailsRepo.upsert("user-1", "token-abc", details, futureExpiresAt());

      Optional<CachedUserDetails> result = userDetailsRepo.findByUserId("user-1");

      assertThat(result).isPresent();
      assertThat(result.get().details().locale()).isEqualTo("it");
      assertThat(result.get().details().carbonioAttributes()).isEqualTo(attrs);
    }

    @Test
    void newerUpsertWins() {
      long now = System.currentTimeMillis();
      userDetailsRepo.upsert("user-1", "token-1", sampleDetails(), now + 10_000);

      UserDetails newer = new UserDetails("it", Map.of());
      UserDetails result = userDetailsRepo.upsert("user-1", "token-2", newer, now + 20_000);

      assertThat(result).isEqualTo(newer);
    }

    @Test
    void olderUpsertLosesAndReturnsDbValue() {
      long now = System.currentTimeMillis();
      UserDetails first = sampleDetails();
      userDetailsRepo.upsert("user-1", "token-1", first, now + 20_000);

      UserDetails older = new UserDetails("it", Map.of());
      UserDetails result = userDetailsRepo.upsert("user-1", "token-2", older, now + 10_000);

      assertThat(result).isEqualTo(first);
    }

    @Test
    void deleteExpiredRemovesOnlyExpired() {
      long now = System.currentTimeMillis();
      userDetailsRepo.upsert("user-1", "token-1", sampleDetails(), now - 1000);
      userDetailsRepo.upsert("user-2", "token-2", sampleDetails(), now + 3_600_000);

      int deleted = userDetailsRepo.deleteExpired();

      assertThat(deleted).isEqualTo(1);
      assertThat(userDetailsRepo.findByUserId("user-1")).isEmpty();
      assertThat(userDetailsRepo.findByUserId("user-2")).isPresent();
    }
  }

  // ---- Full flow: L2 → L1 → SOAP ----

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
      // Pre-populate DB only
      userInfoRepo.upsert(sampleUserInfo(), futureExpiresAt());

      Optional<UserInfo> result = userService.getUserById("user-1", "token-1");

      assertThat(result).contains(sampleUserInfo());
      // Now Caffeine should be populated
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
      // Pre-populate DB only
      userInfoRepo.upsert(sampleUserInfo(), futureExpiresAt());

      Optional<UserInfo> result = userService.getUserByEmail("user@example.com", "token-1");

      assertThat(result).contains(sampleUserInfo());
      assertThat(userInfoCache.getByEmail("user@example.com")).contains(sampleUserInfo());
      verify(mailboxClient, never()).send(any());
    }

    @Test
    void getUserMyself_L2Miss_L1Hit_resolvesFromDb() {
      // Pre-populate both DB tables
      userInfoRepo.upsert(sampleUserInfo(), futureExpiresAt());
      userDetailsRepo.upsert("user-1", "token-1", sampleDetails(), futureExpiresAt());

      Optional<MyselfResult> result = userService.getUserMyself("token-1");

      assertThat(result).isPresent();
      assertThat(result.get().info()).isEqualTo(sampleUserInfo());
      assertThat(result.get().details()).isEqualTo(sampleDetails());
      // Caffeine should now be populated
      assertThat(userInfoCache.getByUserId("user-1")).contains(sampleUserInfo());
      assertThat(userDetailsCache.resolveUserId("token-1")).contains("user-1");
      verify(mailboxClient, never()).send(any());
    }

    @Test
    void getUserMyself_L2Miss_L1Miss_callsSoap() throws Exception {
      when(mailboxClient.send(any())).thenThrow(new MailboxClientException("test"));

      Optional<MyselfResult> result = userService.getUserMyself("token-1");

      assertThat(result).isEmpty();
      verify(mailboxClient).send(any());
    }
  }
}
