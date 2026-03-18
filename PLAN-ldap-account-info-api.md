# Plan: LDAP Account Info API (cosId + domainId from accountId)

## Goal

Create a new REST API endpoint in `carbonio-user-management` that, given an `accountId`,
returns the `cosId` and `domainId` by querying LDAP directly (no auth token required).

## LDAP Query Strategy

Based on analysis of `carbonio-mailbox/store/` LDAP implementation:

### Step 1 — Lookup account by zimbraId
- **Filter:** `(&(zimbraId=<accountId>)(objectClass=zimbraAccount))`
- **Base DN:** root (e.g., empty or configured base)
- **Scope:** SUBTREE
- **Attributes to fetch:** `zimbraCOSId`, `mail`
- **Result:** `zimbraCOSId` is stored directly on the account entry. The `mail` attribute
  contains the email (e.g., `user@example.com`) — split on `@` to get the domain name.

### Step 2 — Lookup domain by name
- **Filter:** `(&(zimbraDomainName=<domainName>)(objectClass=zimbraDomain))`
- **Base DN:** same root
- **Scope:** SUBTREE
- **Attributes to fetch:** `zimbraId`, `zimbraDomainDefaultCOSId`
- **Result:** The domain's `zimbraId` is the `domainId`.

### COS fallback
If `zimbraCOSId` is not set on the account, use the domain's `zimbraDomainDefaultCOSId`.

---

## Decisions

- **Schema naming:** `AccountInfo` — good enough for a spike, refactor later
- **LDAP bind DN:** default `uid=zimbra,cn=admins,cn=zimbra` (same as carbonio-mailbox)
- **LDAP password source:** `config.properties` (spike simplicity)
- **Connection strategy:** `LDAPConnectionPool` (pool size 5)
- **Error handling:** 500 with empty body on LDAP errors

---

## Implementation Steps

### 1. OpenAPI Spec — `resources/user-management.yaml`

Add new tag, path, response, and schema:

```yaml
# Under tags:
- name: Account

# Under paths:
/account/{accountId}:
  get:
    tags:
      - Account
    summary: Gets cosId and domainId for an account by querying LDAP
    operationId: getAccountInfo
    parameters:
      - in: path
        name: accountId
        description: The zimbraId of the account
        required: true
        schema:
          type: string
    responses:
      '200':
        $ref: '#/components/responses/200AccountInfo'
      '404':
        $ref: '#/components/responses/404NotFound'
      '500':
        $ref: '#/components/responses/500ServerError'

# Under components/responses:
200AccountInfo:
  description: COS ID and Domain ID for the account
  content:
    application/json:
      schema:
        $ref: '#/components/schemas/AccountInfo'

# Under components/schemas:
AccountInfo:
  type: object
  properties:
    cosId:
      type: string
    domainId:
      type: string
```

### 2. Regenerate API stubs

Run `mvn generate-sources -pl generated` to produce:
- `AccountApi.java` (JAX-RS resource)
- `AccountApiService.java` (service interface)
- `AccountInfo.java` (model)

### 3. LDAP config — `Constants.java` + `UserManagementConfig.java`

Add LDAP connection constants and config getters:
- `carbonio.ldap.host` (default: `127.78.0.5`)
- `carbonio.ldap.port` (default: `389`)
- `carbonio.ldap.bind-dn` (default: `uid=zimbra,cn=admins,cn=zimbra`)
- `carbonio.ldap.bind-password` (no default — must be configured)
- `carbonio.ldap.base-dn` (default: empty string)

### 4. LdapConnectionProvider — `services/LdapConnectionProvider.java`

Guice `@Singleton` that provides an `LDAPConnectionPool` (UnboundID).
- Created via `@Provides` in `UserManagementModule` using config values.
- Pool size: 5 connections.

### 5. AccountLdapService — `services/AccountLdapService.java`

Business logic class with method:

```java
public Optional<AccountInfo> getCosAndDomainByAccountId(String accountId)
```

- Uses `LDAPConnectionPool` to search LDAP.
- Performs the two queries described above.
- Uses `Filter.createEqualityFilter()` and `Filter.createANDFilter()` to build safe
  filters (prevents LDAP injection).
- Returns `Optional.empty()` if account not found.

### 6. AccountApiController — `controllers/AccountApiController.java`

Implements generated `AccountApiService`:

```java
@RequestScoped
public class AccountApiController implements AccountApiService {
  @Inject AccountLdapService accountLdapService;

  public Response getAccountInfo(String accountId, SecurityContext ctx) {
    return accountLdapService.getCosAndDomainByAccountId(accountId)
        .map(r -> Response.ok().entity(r).build())
        .orElse(Response.status(404).build());
  }
}
```

### 7. Guice wiring — `UserManagementModule.java`

Add bindings:
```java
bind(AccountApi.class);
bind(AccountApiService.class).to(AccountApiController.class);
```

Add `@Provides @Singleton` method for `LDAPConnectionPool`.

### 8. Unit test — `controllers/AccountApiControllerTest.java`

Mock `AccountLdapService`, verify controller delegates correctly and returns
proper HTTP status codes (200, 404).

---

## Todo List

- [x] 1. OpenAPI spec — add `GET /account/{accountId}` (`resources/user-management.yaml`)
- [x] 2. Regenerate API stubs (`mvn generate-sources -pl generated`)
- [x] 3. LDAP config — add constants + config getters (`Constants.java`, `UserManagementConfig.java`)
- [x] 4. LDAPConnectionPool provider (`UserManagementModule.java` — `@Provides @Singleton`)
- [x] 5. AccountLdapService — LDAP query logic (**create** `services/AccountLdapService.java`)
- [x] 6. AccountApiController — REST controller (**create** `controllers/AccountApiController.java`)
- [x] 7. Guice wiring — bind AccountApi + AccountApiService (`UserManagementModule.java`)
- [x] Compile check — passes cleanly
- [ ] 8. Unit test — approach TBD

## Files to create/modify

| File | Action |
|------|--------|
| `resources/user-management.yaml` | Modify — add endpoint + schema |
| `Constants.java` | Modify — add LDAP config constants |
| `UserManagementConfig.java` | Modify — add LDAP getters |
| `UserManagementModule.java` | Modify — add bindings + LDAP pool provider |
| `services/AccountLdapService.java` | **Create** — LDAP query logic |
| `controllers/AccountApiController.java` | **Create** — REST controller |
| `test/.../AccountApiControllerTest.java` | **Create** — unit test |
