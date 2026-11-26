<!--
SPDX-FileCopyrightText: 2026 2026 Zextras <https://www.zextras.com>

SPDX-License-Identifier: AGPL-3.0-only
-->

# Carbonio User Management Service

## Description
Quarkus microservice that exposes user profile data (UserInfo, UserMyself) and admin rights checks via REST. Reads everything from LDAP (no mailbox calls) and caches user data in Caffeine.

## Architecture
- **Multi-module Maven**: `sdk` (REST SDK generated from `app/docs/openapi.yaml`), `app` (Quarkus service)
- **Cache**: exactly two Caffeine in-process caches, per instance, each capped at `CACHE_MAX_ENTRIES` (100000, Caffeine size eviction) with TTL from Consul KV (`cache.userinfo-ttl`, `cache.usermyself-ttl`, both 900 s by default; a /myself entry never outlives its token; 0 disables); everything else (domain, COS, config, server, grants, groups) is read from LDAP on every request; within one request (e.g. a batch) each domain, COS and the global config is read at most once, and every read asks only for the attributes the code uses (`LdapDirectory.*_ATTRIBUTES`, per use: info, myself, rights; add an attribute there when new code reads it). Feature and capability attributes (`carbonioFeature*`, `carbonioWsc*`, ...) are requested by name from the `mailbox-attribute-manager` catalog (`UserMapper.SETTING_ATTRIBUTES`), so a new one needs the catalog version bump
- **Upstream**: LDAP only (UnboundID pool), bound as the read-only `uid=carbonio-user-management,cn=appaccts,cn=zimbra` (its ACLs ship with carbonio-directory-server). URL and StartTLS come from localconfig through `package/carbonio-user-management-ldap-config` (ExecStartPre, written to `/run/carbonio-user-management/ldap.properties`); StartTLS follows mailbox's localconfig rule. Bind DN and password are the Consul KV keys `ldap-username` and `ldap-password`, written by `package/carbonio-user-management-ldap-credentials` in pending-setups before the jar `--setup` (which restarts the service): it creates the entry as the LDAP admin, sets the password and fails if the identity cannot read `cn=config,cn=zimbra` (directory-server without the user-management rules); `--rotate` sets a new password
- **Auth tokens**: `/myself` verifies `ZM_AUTH_TOKEN` locally (HmacSHA1 with `zimbraAuthTokenKey`, expiry, `zimbraAuthTokens` registration, validity value)
- **Rights**: `/internal/rights*` mirrors mailbox carbonio-authz (HardRules, preset and attribute rights, groups, domains, global grant) for account, domain and cos targets; the catalog is the `mailbox-attribute-manager` artifact, keep its version aligned with mailbox
- **Endpoints**: REST `/internal/users/*`, `/internal/rights*`, `/internal/groups/membership` (mesh-gated, no caller authorization); an LDAP failure answers 424
- **Config**: Consul-backed via `carbonio-quarkus-extensions-bootstrap` (`ApplicationConfigService`)
- **Coalescing**: Concurrent requests for the same key are deduplicated via `ConcurrentHashMap<String, CompletableFuture>` in `UserService`

## Key Source Files
```
app/src/main/java/com/zextras/carbonio/user_management/
  UserManagementServiceConfig.java   — config keys, constants
  cache/UserInfoCache.java           — Caffeine cache for UserInfo (by userId + email index)
  cache/UserMyselfCache.java         — Caffeine cache for UserMyself (by token + userId index)
  service/UserService.java           — orchestration: cache → UserDirectory (LDAP) fallback
  ldap/LdapDirectory.java            — LDAP reads, uncached (every read goes to LDAP), attribute projections
  ldap/UserMapper.java               — effective COS, locale, features, capabilities as mailbox computes them
  auth/AuthTokenVerifier.java        — local ZM_AUTH_TOKEN verification
  rights/RightsEvaluator.java        — ACL decisions (mailbox ACLAccessManager semantics)
  rights/GroupMembershipService.java — is an account in a group (id or address), direct flag
  rest/UserResource.java             — REST endpoint (myself's token is the `ZM_AUTH_TOKEN` header, `@HeaderParam`)
  ldap/LdapConnectionPoolProducer.java — LDAP pool (timeouts, StartTLS)
```

## Build & Run
```bash
# Build (uber-jar)
mvn -pl app package -DskipTests

# Unit tests only
mvn verify -DskipITs

# Unit and integration tests (requires Docker)
mvn verify

# Integration tests only
mvn verify -DskipUTs

# Dev mode (requires docker/devmode compose)
mvn -pl app quarkus:dev
```

## Code Conventions
- Java 21, Jakarta EE (CDI, JAX-RS), Quarkus 3.40.1 (managed by `carbonio-quarkus-extensions-parent`)
- SPDX license headers on every file (`AGPL-3.0-only`)
- Records for immutable value types (`UserInfo`, `UserMyself`)
- Conventional commits (`feat:`, `fix:`, `chore:`)
- Unit tests: plain JUnit5 + Mockito (no CDI container)
- Integration tests: `@QuarkusIntegrationTest` + `MailboxStackTestResource` (Testcontainers); mailbox runs only to issue tokens and provision LDAP, `RightsIT` compares rights with `zmprov -l checkRight`

## Things to Know
- Test and coverage standard (Surefire/Failsafe, JaCoCo, Sonar paths) comes from `carbonio-quarkus-extensions-parent`; do not add skip flags, `<argLine>` or JaCoCo executions. Extra JVM flags for tests and for the app started by `@QuarkusIntegrationTest` go in the `testJvmArgs` property of the root pom
- Caffeine TTL is read from Consul at insertion time (not at startup), cached for 60s
- Cache removal listeners run on the calling thread and keep an index entry when the cache already holds a newer value for that key (re-put after expiry); `UserInfoCache.getByEmail` also checks the email of the cached value
- `UserMapper` keys features and capabilities with the catalog's `String` instances (`SETTING_ATTRIBUTES`) so cached /myself entries share them
- `UserMyselfCache` enforces one-token-per-user via a reverse index
- Integration tests need a real Carbonio mailbox stack (Docker images from internal registry)
- `carbonio-quarkus-extensions-bootstrap` is declared explicitly in `app/pom.xml` because removing `carbonio-quarkus-extensions-rate-limit` (which used to pull it in transitively) would otherwise lose `ConfigKey`/`ApplicationConfigService` from compile scope
