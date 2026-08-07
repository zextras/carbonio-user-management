# SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
#
# SPDX-License-Identifier: AGPL-3.0-only

# Native build: the GraalVM runner is a glibc-linked amd64 ELF binary, so the
# base must be glibc (NOT alpine/musl) and the image is amd64-only (a native
# binary is architecture-specific). Mirrors carbonio-tasks-ce.
FROM --platform=linux/amd64 docker.io/library/ubuntu:noble

LABEL maintainer="Zextras <https://www.zextras.com>"
LABEL description="Carbonio User Management Service (native binary)"

RUN groupadd -r carbonio-user-management \
  && useradd -r -g carbonio-user-management carbonio-user-management \
  && mkdir -p /etc/carbonio/user-management

WORKDIR /app

COPY app/target/*-runner /app/carbonio-user-management-runner
RUN chmod +x /app/carbonio-user-management-runner

USER carbonio-user-management

EXPOSE 10000

ENTRYPOINT ["/app/carbonio-user-management-runner", "-Djava.net.preferIPv4Stack=true"]
