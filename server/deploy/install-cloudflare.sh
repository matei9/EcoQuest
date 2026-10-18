#!/usr/bin/env bash
# Green Guardians API on Ubuntu 24.04 behind Cloudflare DNS proxy (TLS is done by Cloudflare).
# Node listens on 0.0.0.0:5200. The firewall is NOT touched: restrict 5200 to Cloudflare IPs yourself.
# Usage (as root, from the repo's server/ directory):
#   sudo bash deploy/install-cloudflare.sh
# Re-running it updates the code and restarts (keeps DB and env).
set -euo pipefail

SRC_DIR="$(cd "$(dirname "$0")/.." && pwd)"
APP_DIR=/opt/green-guardians/server
ENV_FILE=/etc/green-guardians/api.env
DATA_DIR=/var/lib/green-guardians
PORT=5200

echo "==> Packages (Node.js 24 LTS, sqlite3)"
apt-get update
apt-get install -y ca-certificates curl gnupg sqlite3 rsync openssl python3-venv libgl1 libglib2.0-0
# The systemd unit runs /usr/bin/node; a node from nvm/snap/usr/local elsewhere in PATH doesn't count.
if [ ! -x /usr/bin/node ] || [ "$(/usr/bin/node -p 'process.versions.node.split(".")[0]')" -lt 24 ]; then
  curl -fsSL https://deb.nodesource.com/setup_24.x | bash -
  apt-get install -y nodejs
fi

echo "==> System user and directories"
id greenguardians >/dev/null 2>&1 || useradd --system --home "$DATA_DIR" --shell /usr/sbin/nologin greenguardians
install -d -o greenguardians -g greenguardians -m 750 "$DATA_DIR"
install -d -m 755 "$APP_DIR"
install -d -m 750 /etc/green-guardians

echo "==> Code"
rsync -a --delete --exclude node_modules --exclude data --exclude .env --exclude .venv-ocr --exclude __pycache__ "$SRC_DIR/" "$APP_DIR/"
python3 -m venv "$APP_DIR/.venv-ocr"
"$APP_DIR/.venv-ocr/bin/python" -m pip install --disable-pip-version-check --no-cache-dir -r "$APP_DIR/requirements-ocr.txt"
(cd "$APP_DIR" && npm ci --omit=dev --no-audit --no-fund)

if [ ! -f "$ENV_FILE" ]; then
  echo "==> Creating $ENV_FILE"
  sed -e "s#^HOST=.*#HOST=0.0.0.0#" \
      -e "s#^PORT=.*#PORT=$PORT#" \
      -e "s#^DB_PATH=.*#DB_PATH=$DATA_DIR/green-guardians.db#" \
      -e "s#^JWT_SECRET=.*#JWT_SECRET=$(openssl rand -hex 32)#" \
      "$SRC_DIR/.env.example" > "$ENV_FILE"
fi
chown root:greenguardians "$ENV_FILE"
chmod 640 "$ENV_FILE"

echo "==> systemd"
cp "$SRC_DIR/deploy/green-guardians-api.service" /etc/systemd/system/
systemctl daemon-reload
systemctl enable green-guardians-api
systemctl restart green-guardians-api

# Firewall is managed manually by the owner (script no longer touches ufw).

echo "==> Daily SQLite backup (keeps 14 days)"
cat > /etc/cron.daily/green-guardians-backup <<EOF
#!/bin/sh
sqlite3 $DATA_DIR/green-guardians.db ".backup '$DATA_DIR/backup-\$(date +%F).db'"
find $DATA_DIR -name 'backup-*.db' -mtime +14 -delete
EOF
chmod 755 /etc/cron.daily/green-guardians-backup

echo "==> Health check"
for _ in $(seq 1 20); do
  # -s without -S: no "Failed to connect" noise while the service is still starting.
  if curl -fs "http://127.0.0.1:$PORT/api/health"; then
    echo && echo "OK: API on port $PORT. Now configure Cloudflare (deploy/CLOUDFLARE.md)."
    exit 0
  fi
  sleep 1
done
echo "API did not start. Service status and last logs:"
systemctl status green-guardians-api --no-pager || true
journalctl -u green-guardians-api -n 40 --no-pager
exit 1
