#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
JAR=$(mktemp --suffix=.jar)
trap 'rm -f "$JAR"' EXIT
kotlinc app/src/main/java/pt/birds/meteo/Core.kt app/src/main/java/pt/birds/meteo/AdvancedCore.kt tests/CoreTest.kt -include-runtime -d "$JAR"
java -jar "$JAR"
