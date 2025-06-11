FROM openjdk:17-jdk-slim

WORKDIR /app
CMD sh -c 'JAR=$(ls carbonio-user-management-*-jar-with-dependencies.jar | head -n 1) && \
           java -Djava.net.preferIPv4Stack=true \
                -Xms1024m \
                -Xmx2048m \
                -Dcarbonio.mailbox.url=${MAILBOX_PROTOCOL}://${MAILBOX_HOST}:${MAILBOX_PORT} \
                -jar "$JAR"'