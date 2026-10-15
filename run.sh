#!/bin/bash
# Usage: ./run.sh <armeria-version> <spring-boot-version> <mode> <runs>
set -e
cd "$(dirname "$0")"
mvn -q -DskipTests clean -Darmeria.version="$1" -Dspring-boot.version="$2" package
for i in $(seq 1 "$4"); do
  java -jar target/repro.jar "$3" 4 2>/dev/null | grep -E '^(RESULT|SCAN)' | sed "s/^/sb=$2 /"
done
