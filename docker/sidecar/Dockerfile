# SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
#
# SPDX-License-Identifier: AGPL-3.0-only

FROM registry.dev.zextras.com/dev/carbonio-sidecar:devel

RUN apt-get update && apt-get install -y openjdk-21-jre-headless && rm -rf /var/lib/apt/lists/*

# Service registration
COPY package/carbonio-user-management.hcl /consul/config/

# Setup files for consul registration
COPY package/service-protocol.json /etc/carbonio/user-management/service-discover/
COPY package/intentions.json       /etc/carbonio/user-management/service-discover/
COPY package/policies.json         /etc/carbonio/user-management/service-discover/

# App jar (used for --setup only)
COPY app/target/*-runner.jar /usr/share/carbonio/carbonio-user-management.jar

# Setup script wrapper
COPY docker/sidecar/carbonio-user-management-setup /usr/local/bin/carbonio-user-management-setup
RUN chmod +x /usr/local/bin/carbonio-user-management-setup

ENV SERVICE_NAME=carbonio-user-management
ENV TOKEN_FILE=/etc/carbonio/user-management/service-discover/token
ENV SETUP_SCRIPT="carbonio-user-management-setup setup"