#!/bin/sh
#
# Provisions test accounts in Carbonio mailbox.
# Adapted from https://github.com/Zextras/carbonio-dockerization/blob/main/provisioning/provisioning.sh
#
# Requires docker.sock mount and depends on carbonio-mailbox being healthy.
#

echo "Provisioning mailbox accounts..."

MAILBOX_CONTAINER=$(docker ps --format "table {{.Names}}" | grep -E "carbonio-mailbox" | head -1)

if [ -z "$MAILBOX_CONTAINER" ]; then
    echo "Error: Could not find carbonio-mailbox container"
    exit 1
fi

echo "Using mailbox container: $MAILBOX_CONTAINER"

# Mailbox healthcheck already passed (depends_on: service_healthy),
# but wait for zmprov to be functional
MAX_ATTEMPTS=60
ATTEMPT=1

while [ $ATTEMPT -le $MAX_ATTEMPTS ]; do
    OUTPUT=$(docker exec "$MAILBOX_CONTAINER" sh -c "echo 'cd carbonio.localhost' | zmprov 2>&1")

    if echo "$OUTPUT" | grep -q "DOMAIN_EXISTS"; then
        echo "Mailbox ready — domain already exists."
        break
    fi

    if [ $? -eq 0 ] && ! echo "$OUTPUT" | grep -q "ERROR"; then
        echo "Mailbox ready — domain does not exist yet."
        break
    fi

    echo "Attempt $ATTEMPT/$MAX_ATTEMPTS — waiting 5s..."
    sleep 5
    ATTEMPT=$((ATTEMPT + 1))
done

if [ $ATTEMPT -gt $MAX_ATTEMPTS ]; then
    echo "Timeout: mailbox did not become ready"
    exit 1
fi

echo "Executing provisioning commands..."
docker exec "$MAILBOX_CONTAINER" sh -c "cat > /tmp/prov.ls <<'EOF'
cd carbonio.localhost
mcf zimbraSmtpHostname carbonio-postfix
mcf zimbraPublicServiceHostname localhost
mcf zimbraDefaultDomainName carbonio.localhost
ca zextras@carbonio.localhost assext zimbraIsAdminAccount TRUE
ca admin@carbonio.localhost assext zimbraIsAdminAccount TRUE
ca user@carbonio.localhost assext
mc default carbonioFeatureWscEnabled TRUE
EOF
zmprov < /tmp/prov.ls"

if [ $? -eq 0 ]; then
    echo "Provisioning completed successfully."
else
    echo "Note: Some accounts may already exist, which is normal."
fi
