#!/bin/sh

# SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
#
# SPDX-License-Identifier: AGPL-3.0-only

echo '[sidecar] Waiting for consul agent...'
until consul members >/dev/null 2>&1; do sleep 1; done
echo '[sidecar] Consul agent ready.'

consul services register /consul/config/*.hcl

echo '[sidecar] Running consul setup...'
export SETUP_CONSUL_TOKEN="${BOOTSTRAP_TOKEN:-00000000-0000-0000-0000-000000000000}"
java -jar /usr/share/carbonio/carbonio-user-management.jar \
  --setup http://127.0.0.1:8500
unset SETUP_CONSUL_TOKEN

echo "[sidecar] Starting envoy for ${SERVICE_NAME}"
# shellcheck disable=SC2086 # ENVOY_EXTRA_ARGS is deliberately unquoted: it is a
# space-separated list of extra flags, and quoting would collapse it into one argument.
exec consul connect envoy \
  -sidecar-for="${SERVICE_NAME}" \
  -admin-bind=localhost:0 \
  -token-file="${TOKEN_FILE}" \
  ${ENVOY_EXTRA_ARGS:-}
