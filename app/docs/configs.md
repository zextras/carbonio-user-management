# Default Configuration

## Networking Config

Overridable by `/etc/carbonio/user-management/config.properties`

| Key | Default |
| --- | ------- |
| `carbonio.mailbox.internal.host` | `127.78.0.5` |
| `carbonio.mailbox.internal.port` | `20001` |
| `carbonio.service-discover.host` | `127.0.0.1` |
| `carbonio.service-discover.port` | `8500` |
| `carbonio.service.host` | `127.78.0.5` |
| `carbonio.service.port` | `10000` |

## Application Config

Overridable by Consul KV

| Key | Default | If not set | Description |
| --- | ------- | ---------- | ----------- |
| `carbonio-user-management/cache/userinfo-ttl` | `43200` |  |  |
| `carbonio-user-management/cache/usermyself-ttl` | *(not set)* | Uses remaining token validity time |  |
| `carbonio-user-management/server/idle-timeout` | *(not set)* | Quarkus default: 30 minutes | Idle HTTP connection timeout, in seconds |
| `carbonio-user-management/server/max-connections` | *(not set)* | Quarkus default: no limit | Max concurrent HTTP connections accepted by the server |
| `carbonio-user-management/server/max-threads` | *(not set)* | Quarkus default: max(8 x CPUs, 200) | Max threads of the Quarkus worker pool that runs blocking HTTP endpoints and GraphQL; service-specific pools are not affected |
| `carbonio-user-management/server/queue-size` | *(not set)* | Quarkus default: unbounded | Max requests queued waiting for a Quarkus worker thread; service-specific pools are not affected |

