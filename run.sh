#!/bin/sh
set -eu
cd "$(dirname "$0")"

docker build --build-arg ARMERIA_VERSION="${1:-1.42.0}" -t armeria-jarfile-repro:local .
docker run --rm --network none --read-only --tmpfs /tmp:rw,noexec,nosuid,size=64m \
    --cap-drop ALL --security-opt no-new-privileges --pids-limit 64 --memory 512m \
    armeria-jarfile-repro:local
