#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
BOOT_DIR="$HOME/.termux/boot"
BOOT_FILE="$BOOT_DIR/20-csdp-rm3-server"

mkdir -p "$BOOT_DIR"

cat > "$BOOT_FILE" <<EOF
#!/data/data/com.termux/files/usr/bin/sh
termux-wake-lock
cd '$ROOT_DIR'
./run.sh >> '$ROOT_DIR/data/boot.log' 2>&1
EOF

chmod +x "$BOOT_FILE"

echo "Created Termux:Boot script: $BOOT_FILE"
echo "Requirements:"
echo "1. Install Termux:Boot from the same signing source as Termux."
echo "2. Launch Termux:Boot once manually after installation."
echo "3. Disable battery optimization/restrictions for Termux if the device kills background services."
echo "4. Reboot and test /health from another device on the same LAN."
