# Default Configuration

## Networking Config

Overridable by `config.properties`

| Key | Default |
| --- | ------- |
| `carbonio.mailbox.host` | `127.78.0.5` |
| `carbonio.mailbox.port` | `20000` |
| `carbonio.postgresql.host` | `127.78.0.2` |
| `carbonio.postgresql.port` | `5432` |
| `carbonio.service-discover.host` | `127.0.0.1` |
| `carbonio.service-discover.port` | `8500` |
| `carbonio.service.host` | `127.78.0.5` |
| `carbonio.service.port` | `10000` |

## Application Config

Overridable by Consul KV

| Key | Default | If not set |
| --- | ------- | ---------- |
| `carbonio-user-management/cache/userinfo-ttl` | `43200` |  |
| `carbonio-user-management/cache/usermyself-ttl` | *(not set)* |  |
| `carbonio-user-management/database/credentials/db-name` | *(not set)* |  |
| `carbonio-user-management/database/credentials/db-password` | *(not set)* |  |
| `carbonio-user-management/database/credentials/db-username` | *(not set)* |  |
| `carbonio-user-management/database/db-pool-idle-timeout` | *(not set)* | Quarkus default: 5 minutes |
| `carbonio-user-management/database/db-pool-leak-detection` | *(not set)* | Quarkus default: disabled |
| `carbonio-user-management/database/db-pool-max-lifetime` | *(not set)* | Quarkus default: no limit |
| `carbonio-user-management/database/db-pool-max-size` | *(not set)* | Quarkus default: 20 |
| `carbonio-user-management/database/db-pool-min-size` | *(not set)* | Quarkus default: 0 |
| `carbonio-user-management/rate-limit/rest/capacity` | *(not set)* | rate limiting disabled |
| `carbonio-user-management/rate-limit/rest/refill-period-seconds` | *(not set)* | rate limiting disabled |
| `carbonio-user-management/rate-limit/rest/refill-tokens` | *(not set)* | rate limiting disabled |
| `carbonio-user-management/server/idle-timeout` | *(not set)* | Quarkus default: 30s |
| `carbonio-user-management/server/max-connections` | *(not set)* | Quarkus default: no limit |
| `carbonio-user-management/server/max-threads` | *(not set)* | Quarkus default: 200 |
| `carbonio-user-management/server/queue-size` | *(not set)* | Quarkus default: unbounded |

