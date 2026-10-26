#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

# Load .env without overriding variables already present in the environment.
if [[ -f .env ]]; then
    set -a
    # shellcheck disable=SC1091
    source .env
    set +a
fi

if [[ -z "${JWT_SECRET:-}" ]]; then
    echo "ERROR: JWT_SECRET is missing from .env or the environment."
    exit 1
fi

if [[ -z "${SONAR_TOKEN:-}" ]]; then
    echo "ERROR: SONAR_TOKEN is missing from .env or the environment."
    exit 1
fi

if [[ ! -f ./mvnw ]]; then
    echo "ERROR: Maven wrapper mvnw was not found in the repository root."
    exit 1
fi

SONAR_HOST_URL="${SONAR_HOST_URL:-http://localhost:9000}"
SONAR_PROJECT_KEY="${SONAR_PROJECT_KEY:-Auth-System}"
SONAR_PROJECT_NAME="${SONAR_PROJECT_NAME:-Auth System}"

chmod +x ./mvnw

echo "Running tests and SonarQube analysis..."
./mvnw -B clean verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
    "-Dsonar.projectKey=${SONAR_PROJECT_KEY}" \
    "-Dsonar.projectName=${SONAR_PROJECT_NAME}" \
    "-Dsonar.host.url=${SONAR_HOST_URL}" \
    "-Dsonar.token=${SONAR_TOKEN}"

echo "SonarQube analysis completed successfully."
