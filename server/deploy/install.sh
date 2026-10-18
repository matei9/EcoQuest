#!/usr/bin/env bash
# EcoQuest API installer for Ubuntu 24.04.
# Usage (as root, from the repo's server/ directory):
#   sudo DOMAIN=api.exemplu.ro EMAIL=admin@exemplu.ro bash deploy/install.sh
# Re-running it updates the code and restarts the service (keeps DB and env).
set -euo pipefail

: "${DOMAIN:?set DOMAIN, e.g. DOMAIN=api.exemplu.ro}"
: "${EMAIL:?set EMAIL for Lets Encrypt notices}"
SRC_DIR="$(cd "$(dirname "$0")/.." && pwd)"
APP_DIR=/opt/green-guardians/server
ENV_FILE=/etc/green-guardians/api.env
DATA_DIR=/var/lib/green-guardians

echo "==> Packages (Node.js 24 LTS, nginx, certbot, ufw)"
apt-get update
apt-get install -y ca-certificates curl gnupg nginx certbot python3-certbot-nginx ufw sqlite3 rsync openssl python3-venv libgl1 libglib2.0-0
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
  echo "==> Creating $ENV_FILE (edit it to add KILO_API_KEY / TRANZY_API_KEY)"
  sed -e "s#^DB_PATH=.*#DB_PATH=$DATA_DIR/green-guardians.db#" \
      -e "s#^JWT_SECRET=.*#JWT_SECRET=$(openssl rand -hex 32)#" \
      "$SRC_DIR/.env.example" > "$ENV_FILE"
fi
chown root:greenguardians "$ENV_FILE"
chmod 640 "$ENV_FILE"

echo "==> systemd"
cp "$SRC_DIR/deploy/green-guardians-api.service" /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now green-guardians-api
systemctl restart green-guardians-api

echo "==> nginx + TLS for $DOMAIN"
sed "s/__DOMAIN__/$DOMAIN/g" "$SRC_DIR/deploy/nginx.conf.template" > /etc/nginx/sites-available/green-guardians
ln -sf /etc/nginx/sites-available/green-guardians /etc/nginx/sites-enabled/green-guardians
rm -f /etc/nginx/sites-enabled/default
nginx -t && systemctl reload nginx
certbot --nginx --non-interactive --agree-tos -m "$EMAIL" -d "$DOMAIN" --redirect

echo "==> Firewall (5200 stays private, only nginx reaches it)"
ufw allow OpenSSH
ufw allow 'Nginx Full'
ufw --force enable

echo "==> Daily SQLite backup (keeps 14 days)"
cat > /etc/cron.daily/green-guardians-backup <<EOF
#!/bin/sh
sqlite3 $DATA_DIR/green-guardians.db ".backup '$DATA_DIR/backup-\$(date +%F).db'"
find $DATA_DIR -name 'backup-*.db' -mtime +14 -delete
EOF
chmod 755 /etc/cron.daily/green-guardians-backup

sleep 1
curl -fsS "https://$DOMAIN/api/health" && echo && echo "OK: https://$DOMAIN/api"
