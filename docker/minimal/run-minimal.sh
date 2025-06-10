#!/bin/bash

cleanup() {
    echo -e "\n[INFO] Stopping containers..."

    if [ -d "../../../carbonio-mailbox/docker/standalone" ]; then
        pushd ../../../carbonio-mailbox/docker/standalone > /dev/null
        echo "[INFO] Shutting down containers in ../../../carbonio-mailbox/docker/standalone..."
        docker compose down
        popd > /dev/null
    else
        echo "[WARNING] Directory ../../../carbonio-mailbox/docker/standalone not found."
    fi

    if [ -f "docker-compose.yml" ] || [ -f "docker-compose.yaml" ]; then
        echo "[INFO] Shutting down containers in current directory..."
        docker compose down
    fi

    exit
}

trap cleanup SIGINT SIGTERM EXIT

CURR_DIR=$(pwd)

if [ -d "../../../carbonio-mailbox/docker/standalone" ]; then
    pushd ../../../carbonio-mailbox/docker/standalone > /dev/null
    echo "[INFO] Starting containers in background in $(pwd)..."
    docker compose up --build -d
    popd > /dev/null
else
    echo "[ERROR] Directory ../../../carbonio-mailbox/docker/standalone does not exist."
    exit 1
fi

echo "[INFO] Starting containers in foreground mode in $(pwd)..."
docker compose up --build
