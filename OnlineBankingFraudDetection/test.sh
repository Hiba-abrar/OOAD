#!/usr/bin/env bash
# Builds and runs the automated test suite.
cd "$(dirname "$0")"
./build.sh && java -cp out com.obfds.test.FraudSystemTest
