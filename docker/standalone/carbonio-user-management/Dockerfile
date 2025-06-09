FROM openjdk:17-jdk-slim

WORKDIR /app

CMD sh -c 'mkdir -p /etc/carbonio/user-management && \
           echo "carbonio.mailbox.url=http://127.0.0.1:7070" > /etc/carbonio/user-management/config.properties && \
           JAR=$(ls carbonio-user-management-*-jar-with-dependencies.jar | head -n 1) && \
           java -Djava.net.preferIPv4Stack=true \
                -Xms1024m \
                -Xmx2048m \
                -DTASKS_LOG_LEVEL=warn \
                -jar "$JAR"'