# SPDX-FileCopyrightText: 2026 2026 Zextras <https://www.zextras.com>
#
# SPDX-License-Identifier: AGPL-3.0-only

# Create the runtime config dir in a BUILDPLATFORM prep stage so the final
# image needs no target-arch RUN (a RUN in the TARGETPLATFORM stage would
# require QEMU => exec format error on arm64 cross-builds).
FROM --platform=$BUILDPLATFORM eclipse-temurin:21.0.11_10-jdk-alpine AS prep
RUN mkdir -p /out/etc/carbonio/user-management

FROM eclipse-temurin:21.0.11_10-jdk-alpine

WORKDIR /app

# Empty runtime config dir, created QEMU-free above.
COPY --from=prep /out/etc /etc

COPY app/target/*-runner.jar carbonio-user-management.jar

ENTRYPOINT ["java", "--enable-preview", "--enable-native-access=ALL-UNNAMED", "-Djava.net.preferIPv4Stack=true", "-jar", "/app/carbonio-user-management.jar"]
