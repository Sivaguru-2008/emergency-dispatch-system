#!/usr/bin/env bash
# Build and run the Emergency Dispatch System (Linux / macOS / Git Bash).
#   ./run.sh         scripted demo + self-test
#   ./run.sh web     interactive web frontend (opens browser)
#   ./run.sh test    only the correctness self-test
#   ./run.sh menu    interactive console
set -e
cd "$(dirname "$0")"
mkdir -p out
javac -d out $(find src -name '*.java')
java -cp out emergency.Main "$@"
