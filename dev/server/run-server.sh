#!/bin/bash
# Headless: send host once, keep stdin open, exit when java exits (systemd Restart= works).
set -euo pipefail
cd "$(dirname "$0")"

PIPE=$(mktemp -u)
mkfifo "$PIPE"
trap 'rm -f "$PIPE"' EXIT

{
  echo host
  exec tail -f /dev/null
} > "$PIPE" &
FEEDER_PID=$!

java -Xms256M -Xmx512M -jar server.jar < "$PIPE"
EXIT_CODE=$?

kill "$FEEDER_PID" 2>/dev/null || true
wait "$FEEDER_PID" 2>/dev/null || true
exit "$EXIT_CODE"
