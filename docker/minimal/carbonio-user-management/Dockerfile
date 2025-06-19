FROM openjdk:17-jdk-slim

WORKDIR /app

RUN mkdir -p /etc/carbonio/user-management

COPY boot/target/carbonio-user-management-*-jar-with-dependencies.jar /app/

# Default values
ENV CARBONIO_USER_MANAGEMENT_HOST=0.0.0.0
ENV CARBONIO_USER_MANAGEMENT_PORT=10000
ENV CARBONIO_MAILBOX_HOST=127.0.0.1
ENV CARBONIO_MAILBOX_PORT=8080

CMD sh -c 'echo "\n\
carbonio.user-management.host=${CARBONIO_USER_MANAGEMENT_HOST}\n\
carbonio.user-management.port=${CARBONIO_USER_MANAGEMENT_PORT}\n\
\n\
carbonio.mailbox.host=${CARBONIO_MAILBOX_HOST}\n\
carbonio.mailbox.port=${CARBONIO_MAILBOX_PORT}" > /etc/carbonio/user-management/config.properties && \
JAR=$(ls carbonio-user-management-*-jar-with-dependencies.jar | head -n 1) && \
java -Djava.net.preferIPv4Stack=true \
     -Xms1024m \
     -Xmx2048m \
     -jar "$JAR"'