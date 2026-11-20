# SPDX-FileCopyrightText: 2026 2026 Zextras <https://www.zextras.com>
#
# SPDX-License-Identifier: AGPL-3.0-only

FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

RUN mkdir -p /etc/carbonio/user-management

COPY app/target/*-runner.jar carbonio-user-management.jar

ENTRYPOINT ["java", "--enable-preview", "--enable-native-access=ALL-UNNAMED", "-Djava.net.preferIPv4Stack=true", "-jar", "/app/carbonio-user-management.jar"]
