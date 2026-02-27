FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

RUN mkdir -p /etc/carbonio/user-management

COPY app/target/*-runner.jar carbonio-user-management.jar

COPY docker/entrypoint.sh entrypoint.sh

ENTRYPOINT ["/app/entrypoint.sh"]
