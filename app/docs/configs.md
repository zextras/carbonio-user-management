<!--
SPDX-FileCopyrightText: 2026 2026 Zextras <https://www.zextras.com>

SPDX-License-Identifier: AGPL-3.0-only
-->

# Default Configuration

## Networking Config

Overridable by `config.properties`

| Key | Default |
| --- | ------- |
| `carbonio.mailbox.host` | `127.78.0.5` |
| `carbonio.mailbox.port` | `20000` |
| `carbonio.service-discover.host` | `127.0.0.1` |
| `carbonio.service-discover.port` | `8500` |
| `carbonio.service.host` | `127.78.0.5` |
| `carbonio.service.port` | `10000` |

## Application Config

Overridable by Consul KV

| Key | Default | If not set |
| --- | ------- | ---------- |
| `carbonio-user-management/cache/userinfo-ttl` | `43200` |  |
| `carbonio-user-management/cache/usermyself-ttl` | *(not set)* | Uses remaining token validity time |
| `carbonio-user-management/rate-limit/rest/capacity` | *(not set)* | Rate limiting disabled |
| `carbonio-user-management/rate-limit/rest/refill-period-seconds` | *(not set)* | Rate limiting disabled |
| `carbonio-user-management/rate-limit/rest/refill-tokens` | *(not set)* | Rate limiting disabled |
| `carbonio-user-management/server/idle-timeout` | *(not set)* | Quarkus default: 30s |
| `carbonio-user-management/server/max-connections` | *(not set)* | Quarkus default: no limit |
| `carbonio-user-management/server/max-threads` | *(not set)* | Quarkus default: 200 |
| `carbonio-user-management/server/queue-size` | *(not set)* | Quarkus default: unbounded |

