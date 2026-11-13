<!--
SPDX-FileCopyrightText: 2026 2026 Zextras <https://www.zextras.com>

SPDX-License-Identifier: AGPL-3.0-only
-->

# Carbonio User Management Service

## Description
Quarkus microservice that exposes user profile data (UserInfo, UserMyself) via REST and gRPC. Fetches data from Carbonio Mailbox via internal REST API and caches results in Caffeine.

## Architecture
- **Multi-module Maven**: `sdk` (gRPC protobuf stubs), `app` (Quarkus service)
- **Cache**: Caffeine in-process cache with configurable TTL via Consul KV (`cache.userinfo-ttl`, `cache.usermyself-ttl`)
- **Upstream**: Mailbox internal REST API (`carbonio-mailbox-internal-api`, port 20001 via Consul mesh)
- **Endpoints**: REST (`/users/*`) + gRPC (UserManagementService)
- **Config**: Consul-backed via `carbonio-quarkus-extensions-bootstrap` (`ApplicationConfigService`)
- **Coalescing**: Concurrent requests for the same key are deduplicated via `ConcurrentHashMap<String, CompletableFuture>` in `UserService`

## Key Source Files
```
app/src/main/java/com/zextras/carbonio/user_management/
  UserManagementServiceConfig.java   — config keys, constants
  cache/UserInfoCache.java           — Caffeine cache for UserInfo (by userId + email index)
  cache/UserMyselfCache.java         — Caffeine cache for UserMyself (by token + userId index)
  service/UserService.java           — orchestration: cache → internal REST API fallback
  grpc/GrpcHandler.java              — gRPC endpoint
  rest/UserResource.java             — REST endpoint (myself's token is an explicit @CookieParam)
  producer/MailboxClientProducer.java — mailbox internal REST API client CDI producer
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
- Integration tests: `@QuarkusIntegrationTest` + `MailboxStackTestResource` (Testcontainers)

## Things to Know
- Test and coverage standard (Surefire/Failsafe, JaCoCo, Sonar paths) comes from `carbonio-quarkus-extensions-parent`; do not add skip flags, `<argLine>` or JaCoCo executions. Extra JVM flags for tests and for the app started by `@QuarkusIntegrationTest` go in the `testJvmArgs` property of the root pom
- Caffeine TTL is read from Consul at insertion time (not at startup), cached for 60s
- `UserMyselfCache` enforces one-token-per-user via a reverse index
- Integration tests need a real Carbonio mailbox stack (Docker images from internal registry)
- Branch `quarkus-refactor-and-cache` is the active development branch
- `carbonio-quarkus-extensions-bootstrap` is declared explicitly in `app/pom.xml` because removing `carbonio-quarkus-extensions-rate-limit` (which used to pull it in transitively) would otherwise lose `ConfigKey`/`ApplicationConfigService` from compile scope
