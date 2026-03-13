#!/bin/sh
#
# Populates Consul KV with configuration needed by carbonio-user-management.
# Runs inside the consul network namespace (network_mode: "service:consul").
#

set -e

CONSUL_HTTP_ADDR="http://localhost:8500"
SERVICE="carbonio-user-management"

# Wait for Consul API to be available and accept KV writes
echo "Waiting for Consul API..."
for i in $(seq 1 30); do
  if curl -sf "${CONSUL_HTTP_ADDR}/v1/status/leader" >/dev/null 2>&1 \
     && curl -sf -X PUT --data "probe" "${CONSUL_HTTP_ADDR}/v1/kv/__probe" >/dev/null 2>&1 \
     && curl -sf -X DELETE "${CONSUL_HTTP_ADDR}/v1/kv/__probe" >/dev/null 2>&1; then
    echo "Consul API ready after ${i} attempts"
    break
  fi
  if [ "$i" -eq 30 ]; then
    echo "ERROR: Consul API not available after 30 attempts"
    exit 1
  fi
  sleep 1
done

echo "Consul KV ready for ${SERVICE}."
