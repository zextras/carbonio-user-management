FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

RUN mkdir -p /etc/carbonio/user-management

COPY ../../boot/target/carbonio-user-management-*-jar-with-dependencies.jar .

COPY docker/minimal/carbonio-user-management/entrypoint.sh entrypoint.sh

ENTRYPOINT ["/app/entrypoint.sh"]