#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="$HOME/.csdp-server.env"
DATA_DIR="$ROOT_DIR/data"

pkg update -y
pkg install -y python openssh git

mkdir -p "$DATA_DIR"

if [[ ! -f "$ENV_FILE" ]]; then
  TOKEN="$(python - <<'PY'
import secrets
print(secrets.token_urlsafe(32))
PY
)"
  cat > "$ENV_FILE" <<EOF
export CSDP_SERVER_TOKEN='$TOKEN'
export CSDP_SERVER_HOST='0.0.0.0'
export CSDP_SERVER_PORT='8765'
export CSDP_SERVER_DB='$DATA_DIR/telemetry.db'
export CSDP_SERVER_LOG='$DATA_DIR/server.log'
EOF
  chmod 600 "$ENV_FILE"
  echo "Created $ENV_FILE with a random API token."
else
  echo "$ENV_FILE already exists; leaving it unchanged."
fi

chmod +x "$ROOT_DIR/run.sh" "$ROOT_DIR/stop.sh" "$ROOT_DIR/install-boot.sh"

echo
echo "Installation complete."
echo "Start server with:"
echo "  cd '$ROOT_DIR' && ./run.sh"
echo
echo "Optional SSH server:"
echo "  passwd"
echo "  sshd"
echo "Termux SSH listens on port 8022 by default."
