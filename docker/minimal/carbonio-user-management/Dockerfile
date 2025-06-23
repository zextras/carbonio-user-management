FROM openjdk:17-jdk-slim

WORKDIR /app

COPY boot/target/carbonio-user-management-*-SNAPSHOT-jar-with-dependencies.jar .

CMD sh -c 'JAR=$(ls carbonio-user-management-*-jar-with-dependencies.jar | head -n 1) && \
           java -Djava.net.preferIPv4Stack=true \
                           -Xms1024m \
                           -Xmx2048m \
                           -Dcarbonio.mailbox.host=${MAILBOX_HOST} \
                           -Dcarbonio.mailbox.port=${MAILBOX_PORT} \
                           -Dcarbonio.user-management.host=${USER_MANAGEMENT_HOST} \
                           -Dcarbonio.user-management.port=${USER_MANAGEMENT_PORT} \
                           -jar "$JAR"'