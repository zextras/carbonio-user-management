# Carbonio User Management Service

## Description
Quarkus microservice that exposes user profile data (UserInfo, UserMyself) via REST and gRPC. Fetches data from Carbonio Mailbox via SOAP and caches results in Caffeine.

## Architecture
- **Multi-module Maven**: `sdk` (gRPC protobuf stubs), `app` (Quarkus service)
- **Cache**: Caffeine in-process cache with configurable TTL via Consul KV (`cache.userinfo-ttl`, `cache.usermyself-ttl`)
- **Upstream**: Mailbox SOAP client (`carbonio-mailbox-sdk`)
- **Endpoints**: REST (`/users/*`) + gRPC (UserManagementService)
- **Config**: Consul-backed via `carbonio-quarkus-extensions-bootstrap` (`ApplicationConfigService`)
- **Coalescing**: Concurrent requests for the same key are deduplicated via `ConcurrentHashMap<String, CompletableFuture>` in `UserService`

## Key Source Files
```
app/src/main/java/com/zextras/carbonio/user_management/
  UserManagementServiceConfig.java   — config keys, constants
  cache/UserInfoCache.java           — Caffeine cache for UserInfo (by userId + email index)
  cache/UserMyselfCache.java         — Caffeine cache for UserMyself (by token + userId index)
  service/UserService.java           — orchestration: cache → SOAP fallback
  grpc/GrpcHandler.java              — gRPC endpoint
  rest/UserResource.java             — REST endpoint
  rest/TokenAuthFilter.java          — token extraction filter
  producer/MailboxClientProducer.java — SOAP client CDI producer
```

## Build & Run
```bash
# Build (uber-jar)
mvn -pl app package -DskipTests

# Unit tests
mvn -pl app test

# Integration tests (requires Docker)
mvn -pl app verify -Dskip.integration.tests=false

# Dev mode (requires docker/devmode compose)
mvn -pl app quarkus:dev
```

## Code Conventions
- Java 21, Jakarta EE (CDI, JAX-RS), Quarkus 3.27.2
- SPDX license headers on every file (`AGPL-3.0-only`)
- Records for immutable value types (`UserInfo`, `UserMyself`)
- Conventional commits (`feat:`, `fix:`, `chore:`)
- Unit tests: plain JUnit5 + Mockito (no CDI container)
- Integration tests: `@QuarkusIntegrationTest` + `MailboxStackTestResource` (Testcontainers)

## Things to Know
- Caffeine TTL is read from Consul at insertion time (not at startup), cached for 60s
- `UserMyselfCache` enforces one-token-per-user via a reverse index
- Integration tests need a real Carbonio mailbox stack (Docker images from internal registry)
- Branch `quarkus-refactor-and-cache` is the active development branch
