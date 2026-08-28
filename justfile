default:
    @just --list

# Build jar and start all services in Podman
up:
    LEDGER_HMAC_SECRET=dev_secret_key_123 API_AUTH_TOKEN=dev_secret_key_123 /home/rakeshpc/.m2/wrapper/dists/apache-maven-3.9.6/a53741d1/bin/mvn package -DskipTests -f core-node/pom.xml
    podman-compose up --build -d

# Stop and remove running Podman containers
down:
    podman-compose down

# View logs of running containers
logs:
    podman-compose logs -f

# Restart all Podman services
restart:
    podman-compose restart

# Run maven test suite
test:
    LEDGER_HMAC_SECRET=testsecret API_AUTH_TOKEN=test-auth-token /home/rakeshpc/.m2/wrapper/dists/apache-maven-3.9.6/a53741d1/bin/mvn test -f core-node/pom.xml
