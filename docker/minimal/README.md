# Run UM locally with Docker

This minimal configuration assumes there already is a local Mailbox instance running.
You can update the `docker-compose.yml` file to point to your local Mailbox instance, changing the ENV variables.
A first "dockerized" way to get started is to run the Mailbox standalone container, and once that's working run this minimal setup.

Steps:
    1. `mvn clean install -DskipTests=true`
    2. `cd docker/minimal`
    3. `docker compose up --build`
    4. Backend will be accessible on `127.78.0.5:10000`