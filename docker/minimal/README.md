# Run UM locally with Docker

This minimal setup includes all necessary dependencies without mocks.

Steps:
    1. `mvn clean install -DskipTests=true`
    2. `cd docker/minimal`
    3. `docker compose up --build`
    4. Browse Carbonio on `http://localhost:9000/`, backend accessible on `http://localhost:20001`
    5. Login using `test@demo.zextras.io`/`password`

Possible configs for UM:
  - CARBONIO_USER_MANAGEMENT_HOST
  - CARBONIO_USER_MANAGEMENT_PORT
  - CARBONIO_MAILBOX_HOST
  - CARBONIO_MAILBOX_PORT