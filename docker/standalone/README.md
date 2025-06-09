# Run UM locally with Docker

Steps:
    1. `mvn clean install -DskipTests=true`
    2. `cd docker/standalone`
    3. `docker compose up --build`
    4. Backend accessible on `http://127.78.0.16:10000`
    5. Test users: `test1`,`test2`,`test3`/`assext`