#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="$HOME/.csdp-server.env"
PID_FILE="$ROOT_DIR/data/server.pid"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE. Run ./install.sh first." >&2
  exit 1
fi

# shellcheck disable=SC1090
source "$ENV_FILE"
mkdir -p "$ROOT_DIR/data"

if [[ -f "$PID_FILE" ]]; then
  OLD_PID="$(cat "$PID_FILE" || true)"
  if [[ -n "$OLD_PID" ]] && kill -0 "$OLD_PID" 2>/dev/null; then
    echo "Server already running with PID $OLD_PID"
    exit 0
  fi
  rm -f "$PID_FILE"
fi

termux-wake-lock || true

cd "$ROOT_DIR"
nohup python server.py >> "$ROOT_DIR/data/nohup.log" 2>&1 &
PID=$!
echo "$PID" > "$PID_FILE"

sleep 1
if ! kill -0 "$PID" 2>/dev/null; then
  echo "Server failed to start. Check data/nohup.log and data/server.log." >&2
  rm -f "$PID_FILE"
  exit 1
fi

echo "CSDP-RM3 telemetry server started."
echo "PID: $PID"
echo "Port: ${CSDP_SERVER_PORT:-8765}"
echo "Health check on this phone: curl http://127.0.0.1:${CSDP_SERVER_PORT:-8765}/health"
echo "API token is stored in $ENV_FILE (chmod 600)."
