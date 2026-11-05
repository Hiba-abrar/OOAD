#!/usr/bin/env bash
# Builds (if needed) and launches the interactive console application.
cd "$(dirname "$0")"
[ -d out/com ] || ./build.sh
java -cp out com.obfds.app.Main
