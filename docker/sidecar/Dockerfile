# SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
#
# SPDX-License-Identifier: AGPL-3.0-only

# Native build: the sidecar runs --setup via the GraalVM runner (glibc amd64
# ELF), so no JRE is needed and the image is amd64-only. Mirrors the app image.
FROM --platform=linux/amd64 registry.dev.zextras.com/dev/carbonio-sidecar:devel

# Service registration
COPY package/carbonio-user-management.hcl /consul/config/

# Setup files for consul registration
COPY package/service-protocol.json /etc/carbonio/user-management/service-discover/
COPY package/intentions.json       /etc/carbonio/user-management/service-discover/
COPY package/policies.json         /etc/carbonio/user-management/service-discover/

# Native runner (used for --setup only)
COPY --chmod=755 app/target/*-runner /usr/share/carbonio/carbonio-user-management-runner

# Setup script wrapper
COPY --chmod=755 docker/sidecar/carbonio-user-management-setup /usr/local/bin/carbonio-user-management-setup

ENV SERVICE_NAME=carbonio-user-management
ENV TOKEN_FILE=/etc/carbonio/user-management/service-discover/token
ENV SETUP_SCRIPT="carbonio-user-management-setup setup"
