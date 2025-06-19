# Run UM locally with Docker

This minimal configuration runs the standalone version of Mailbox and User Management (UM) in Docker.

Steps:
    1. `mvn clean install -DskipTests=true`
    2. `cd docker/minimal`
    3. `docker compose up --build`
    4. User management backend will be accessible on `127.0.0.1:20001`

# Note:
You can use a few ENV variables to configure the UM backend, in particular:
    - CARBONIO_USER_MANAGEMENT_HOST
    - CARBONIO_USER_MANAGEMENT_PORT
    - CARBONIO_MAILBOX_HOST
    - CARBONIO_MAILBOX_PORT