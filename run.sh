#!/usr/bin/env bash

set -Eeuo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

INTERVAL=1
PID=""

# Load .env without overriding existing environment variables.
if [[ -f .env ]]; then
    set -a
    source .env
    set +a
fi

if [[ -z "${JWT_SECRET:-}" ]]; then
    echo "ERROR: JWT_SECRET is missing from .env or the environment."
    exit 1
fi

if [[ ! -f ./mvnw ]]; then
    echo "ERROR: Maven wrapper mvnw was not found."
    exit 1
fi

chmod +x ./mvnw

snapshot() {
    {
        [[ ! -f pom.xml ]] || stat -c '%Y %n' pom.xml 2>/dev/null \
            || stat -f '%m %N' pom.xml 2>/dev/null || true

        if [[ -d src ]]; then
            find src -type f \
                \( -name '*.java' -o -name '*.properties' \
                -o -name '*.yml' -o -name '*.yaml' \
                -o -name '*.xml' \) -print0 |
                xargs -0 -r stat -c '%Y %n' 2>/dev/null ||
            find src -type f \
                \( -name '*.java' -o -name '*.properties' \
                -o -name '*.yml' -o -name '*.yaml' \
                -o -name '*.xml' \) -print0 |
                xargs -0 -r stat -f '%m %N' 2>/dev/null || true
        fi
    } | sort
}

stop_server() {
    if [[ -n "$PID" ]] && kill -0 "$PID" 2>/dev/null; then
        echo "Stopping application..."
        kill "$PID" 2>/dev/null || true
        wait "$PID" 2>/dev/null || true
    fi
    PID=""
}

cleanup() {
    stop_server
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

while true; do
    echo
    echo "Building application..."

    if ! ./mvnw -B clean install; then
        echo "Build failed. Waiting for source changes..."
        PREVIOUS="$(snapshot)"

        while true; do
            sleep "$INTERVAL"
            CURRENT="$(snapshot)"
            if [[ "$CURRENT" != "$PREVIOUS" ]]; then
                break
            fi
        done

        continue
    fi

    echo "Starting Spring Boot..."
    ./mvnw spring-boot:run &
    PID=$!

    PREVIOUS="$(snapshot)"
    echo "Watching source files. Press Ctrl+C to stop."

    while true; do
        sleep "$INTERVAL"

        CURRENT="$(snapshot)"

        if [[ "$CURRENT" != "$PREVIOUS" ]]; then
            echo "Changes detected. Restarting..."
            PREVIOUS="$CURRENT"
            stop_server
            break
        fi

        if ! kill -0 "$PID" 2>/dev/null; then
            echo "Application process exited. Restarting..."
            wait "$PID" 2>/dev/null || true
            PID=""
            break
        fi
    done
done