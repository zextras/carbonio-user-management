FROM localhost/carbonio-sidecar-base:latest

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
