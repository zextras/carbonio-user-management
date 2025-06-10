#!/bin/sh

mkdir -p /etc/carbonio/user-management

cat <<EOF > /etc/carbonio/user-management/config.properties
carbonio.mailbox.url=${MAILBOX_PROTOCOL:-http}://${MAILBOX_HOST:-127.0.0.1}:${MAILBOX_PORT:-7070}
EOF

JAR=$(ls carbonio-user-management-*-jar-with-dependencies.jar | head -n 1)
exec java \
  -Djava.net.preferIPv4Stack=true \
  -Xms1024m \
  -Xmx2048m \
  -jar "$JAR"