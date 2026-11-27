# SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
#
# SPDX-License-Identifier: AGPL-3.0-only

# The setup jar needs a JRE. Installing openjdk-21-jre-headless via apt in the
# final stage runs target-arch maintainer scripts (no QEMU => exec format error
# on arm64). Instead, pull the JRE from the official multi-arch eclipse-temurin
# jammy image (glibc, matches the ubuntu-jammy carbonio-sidecar base); buildx
# selects the arch-matched layer, so no target-arch RUN and no QEMU.
FROM docker.io/library/eclipse-temurin:21-jre-jammy AS jre

FROM --platform=$BUILDPLATFORM docker.io/library/alpine:3.23 AS consul-template
ARG TARGETARCH
ARG CONSUL_TEMPLATE_VERSION=0.42.1
RUN apk add --no-cache curl unzip && \
    curl -fsSL -o /tmp/ct.zip "https://releases.hashicorp.com/consul-template/${CONSUL_TEMPLATE_VERSION}/consul-template_${CONSUL_TEMPLATE_VERSION}_linux_${TARGETARCH}.zip" && \
    curl -fsSL -o /tmp/ct.sums "https://releases.hashicorp.com/consul-template/${CONSUL_TEMPLATE_VERSION}/consul-template_${CONSUL_TEMPLATE_VERSION}_SHA256SUMS" && \
    echo "$(grep "_linux_${TARGETARCH}.zip" /tmp/ct.sums | cut -d' ' -f1)  /tmp/ct.zip" | sha256sum -c - && \
    unzip -j /tmp/ct.zip consul-template -d /out && \
    chmod +x /out/consul-template

FROM registry.dev.zextras.com/dev/carbonio-sidecar:main

# Arch-matched JRE copied from the multi-arch temurin image (QEMU-free).
COPY --from=jre /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="/opt/java/openjdk/bin:${PATH}"

# Service registration
COPY --from=consul-template /out/consul-template /usr/local/bin/consul-template
COPY package/carbonio-user-management-template.hcl /consul/templates/carbonio-user-management-template.hcl

# Setup files for consul registration
COPY package/service-protocol.json /etc/carbonio/user-management/service-discover/
COPY package/intentions.json       /etc/carbonio/user-management/service-discover/
COPY package/policies.json         /etc/carbonio/user-management/service-discover/

# App jar (used for --setup only)
COPY app/target/*-runner.jar /usr/share/carbonio/carbonio-user-management.jar

# Setup script wrapper (COPY --chmod avoids a target-arch RUN — QEMU-free)
COPY --chmod=755 docker/sidecar/carbonio-user-management-setup /usr/local/bin/carbonio-user-management-setup

ENV SERVICE_NAME=carbonio-user-management
ENV TOKEN_FILE=/etc/carbonio/user-management/service-discover/token
ENV SETUP_SCRIPT="carbonio-user-management-setup setup"

COPY --chmod=755 docker/sidecar/carbonio-user-management-hcl-entrypoint /usr/local/bin/carbonio-user-management-hcl-entrypoint
ENTRYPOINT ["carbonio-user-management-hcl-entrypoint"]
