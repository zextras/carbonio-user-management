FROM registry.access.redhat.com/ubi9/ubi-minimal:9.5

WORKDIR /app

RUN mkdir -p /etc/carbonio/user-management

COPY app/target/*-runner carbonio-user-management

ENTRYPOINT ["/app/carbonio-user-management", "-Djava.net.preferIPv4Stack=true"]
