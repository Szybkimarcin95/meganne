#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_FILE="$ROOT_DIR/data/server.pid"

if [[ ! -f "$PID_FILE" ]]; then
  echo "Server PID file not found; nothing to stop."
  exit 0
fi

PID="$(cat "$PID_FILE" || true)"
if [[ -n "$PID" ]] && kill -0 "$PID" 2>/dev/null; then
  kill "$PID"
  for _ in {1..20}; do
    if ! kill -0 "$PID" 2>/dev/null; then
      break
    fi
    sleep 0.1
  done
  if kill -0 "$PID" 2>/dev/null; then
    kill -9 "$PID" || true
  fi
  echo "Stopped server PID $PID"
else
  echo "PID $PID is not running."
fi

rm -f "$PID_FILE"
