FROM docker.io/envoyproxy/envoy:v1.27-latest
COPY --from=docker.io/hashicorp/consul:1.17 /bin/consul /usr/local/bin/consul
RUN apt-get update && apt-get install -y jq && rm -rf /var/lib/apt/lists/*

# Fake service-discover binary — returns a predefined bootstrap token
RUN printf '#!/bin/sh\necho "00000000-0000-0000-0000-000000000000"\n' > /usr/local/bin/service-discover \
    && chmod +x /usr/local/bin/service-discover

# Service registration (registered via consul services register)
COPY package/carbonio-user-management.hcl /consul/config/

# Setup files at the path the setup script expects
COPY package/service-protocol.json /etc/carbonio/user-management/service-discover/
COPY package/intentions.json       /etc/carbonio/user-management/service-discover/
COPY package/policies.json         /etc/carbonio/user-management/service-discover/

# Setup script (runs as-is from the package)
COPY package/carbonio-user-management-setup /usr/local/bin/carbonio-user-management-setup
RUN chmod +x /usr/local/bin/carbonio-user-management-setup

ENV SERVICE_NAME=carbonio-user-management

ENTRYPOINT ["/bin/sh", "-c", "\
  echo '[sidecar] Waiting for consul agent...' && \
  until consul members >/dev/null 2>&1; do sleep 1; done && \
  echo '[sidecar] Consul agent ready.' && \
  consul services register /consul/config/*.hcl && \
  (carbonio-user-management-setup || true) && \
  echo '[sidecar] Starting envoy for $SERVICE_NAME' && \
  exec consul connect envoy \
    -sidecar-for=$SERVICE_NAME \
    -admin-bind=localhost:0 \
"]
