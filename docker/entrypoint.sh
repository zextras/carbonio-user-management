#!/bin/sh

exec java -Djava.net.preferIPv4Stack=true \
     -Xms1024m \
     -Xmx2048m \
     -jar carbonio-user-management.jar
