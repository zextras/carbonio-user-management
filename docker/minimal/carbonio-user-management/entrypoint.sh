#!/bin/sh

echo "" > /etc/carbonio/user-management/config.properties

addEnvToProperties() {
  if [ -n "$2" ];
  then echo "$1=$2" >> /etc/carbonio/user-management/config.properties;
  else echo "$1 is not set. Skipping it.";
  fi
}

addEnvToProperties "carbonio.user-management.host" "${CARBONIO_USER_MANAGEMENT_HOST}"
addEnvToProperties "carbonio.user-management.port" "${CARBONIO_USER_MANAGEMENT_PORT}"

addEnvToProperties "carbonio.mailbox.host" "${CARBONIO_MAILBOX_HOST}"
addEnvToProperties "carbonio.mailbox.port" "${CARBONIO_MAILBOX_PORT}"

addEnvToProperties "carbonio.mailbox.internal.host" "${CARBONIO_MAILBOX_INTERNAL_HOST}"
addEnvToProperties "carbonio.mailbox.internal.port" "${CARBONIO_MAILBOX_INTERNAL_PORT}"

JAR=$(ls carbonio-user-management-*-jar-with-dependencies.jar | head -n 1)

exec java -Djava.net.preferIPv4Stack=true \
     -Xms1024m \
     -Xmx2048m \
     -jar "$JAR"
