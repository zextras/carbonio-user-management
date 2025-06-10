#!/bin/bash

cleanup() {
    echo -e "\n[INFO] Chiusura dei container in corso..."

    if [ -d "../../../carbonio-mailbox/docker/standalone" ]; then
        pushd ../../../carbonio-mailbox/docker/standalone > /dev/null
        echo "[INFO] Arresto dei container in ../../../carbonio-mailbox/docker/standalone..."
        docker compose down
        popd > /dev/null
    else
        echo "[WARNING] Directory ../../../carbonio-mailbox/docker/standalone non trovata."
    fi

    if [ -f "docker-compose.yml" ] || [ -f "docker-compose.yaml" ]; then
        echo "[INFO] Arresto dei container nella directory corrente..."
        docker compose down
    fi

    exit
}

trap cleanup SIGINT SIGTERM EXIT

CURR_DIR=$(pwd)

if [ -d "../../../carbonio-mailbox/docker/standalone" ]; then
    pushd ../../../carbonio-mailbox/docker/standalone > /dev/null
    echo "[INFO] Avvio dei container in background in $(pwd)..."
    docker compose up --build -d
    popd > /dev/null
else
    echo "[ERROR] La directory ../../../carbonio-mailbox/docker/standalone non esiste."
    exit 1
fi

echo "[INFO] Avvio dei container in modalità foreground in $(pwd)..."
docker compose up --build
