# SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
#
# SPDX-License-Identifier: AGPL-3.0-only

FROM registry.dev.zextras.com/dev/carbonio-sidecar:latest

RUN apt-get update && apt-get install -y openjdk-21-jre-headless && rm -rf /var/lib/apt/lists/*

# Service registration
COPY package/carbonio-user-management.hcl /consul/config/

# Setup files for consul registration
COPY package/service-protocol.json /etc/carbonio/user-management/service-discover/
COPY package/intentions.json       /etc/carbonio/user-management/service-discover/
COPY package/policies.json         /etc/carbonio/user-management/service-discover/

# App jar (used for --setup only)
COPY app/target/*-runner.jar /usr/share/carbonio/carbonio-user-management.jar

COPY docker/sidecar/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

ENV SERVICE_NAME=carbonio-user-management
ENV TOKEN_FILE=/etc/carbonio/user-management/service-discover/token

ENTRYPOINT ["entrypoint.sh"]
