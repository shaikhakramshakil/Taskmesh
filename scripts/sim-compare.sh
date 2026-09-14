#!/usr/bin/env bash
# Run the scheduling comparison (the PRD killer-feature table).
# Usage: scripts/sim-compare.sh [--jobs 10000] [simulator args...]
set -euo pipefail
cd "$(dirname "$0")/../backend"
../scripts/mvnw.sh -q -pl taskmesh-simulator -am package -DskipTests
export JAVA_HOME="$HOME/opt/jdk-21"
exec "$JAVA_HOME/bin/java" -jar taskmesh-simulator/target/taskmesh-simulator-0.1.0-exec.jar "$@"
