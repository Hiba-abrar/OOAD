#!/usr/bin/env bash
# Compiles the application and the test suite into ./out
set -e
cd "$(dirname "$0")"
rm -rf out && mkdir -p out
javac -d out $(find src/main/java -name "*.java")
javac -cp out -d out $(find src/test/java -name "*.java")
echo "Build successful."
