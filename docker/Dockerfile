# SPDX-FileCopyrightText: 2026 2026 Zextras <https://www.zextras.com>
#
# SPDX-License-Identifier: AGPL-3.0-only

FROM registry.access.redhat.com/ubi9/ubi-minimal:9.5

RUN microdnf install -y java-21-openjdk-headless && microdnf clean all

WORKDIR /app

RUN mkdir -p /etc/carbonio/user-management

COPY app/target/*-runner.jar carbonio-user-management.jar

ENTRYPOINT ["java", "-Djava.net.preferIPv4Stack=true", "-jar", "/app/carbonio-user-management.jar"]
