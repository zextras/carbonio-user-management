# SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
#
# SPDX-License-Identifier: AGPL-3.0-only

FROM registry.dev.zextras.com/dev/carbonio-sidecar:latest

# Service registration
COPY package/carbonio-user-management.hcl /consul/config/

# Setup files
COPY package/service-protocol.json /etc/carbonio/user-management/service-discover/
COPY package/intentions.json       /etc/carbonio/user-management/service-discover/
COPY package/policies.json         /etc/carbonio/user-management/service-discover/

# Setup script
COPY package/carbonio-user-management-setup /usr/local/bin/carbonio-user-management-setup
RUN chmod +x /usr/local/bin/carbonio-user-management-setup

ENV SERVICE_NAME=carbonio-user-management
ENV SETUP_SCRIPT="carbonio-user-management-setup"
ENV TOKEN_FILE=/etc/carbonio/user-management/service-discover/token
