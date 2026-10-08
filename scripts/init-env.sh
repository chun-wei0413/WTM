#!/usr/bin/env bash
# Creates .env with freshly generated random secrets, and the folders the site stores its data in.
# The same as scripts/init-env.ps1, for macOS and Linux. It never overwrites an existing .env and never prints the secrets.
#
#   scripts/init-env.sh                 data under ~/wtm-data
#   scripts/init-env.sh /Volumes/Big/wtm-data
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT/.env"
DATA_DIR="${1:-$HOME/wtm-data}"

# One folder per kind of data, so each can be backed up or cleaned on its own.
make_folders() {
    for name in postgres images inbox logs backups; do
        mkdir -p "$1/$name"
    done
}

# URL-safe characters only, so the values are safe in .env, YAML and docker-compose.
secret() {
    head -c "$1" /dev/urandom | base64 | tr -d '=\n' | tr '+/' '-_'
}

if [ -f "$ENV_FILE" ]; then
    echo ".env already exists; leaving its secrets untouched."
    if ! grep -q '^WTM_DATA_DIR=' "$ENV_FILE"; then
        echo "WTM_DATA_DIR=$DATA_DIR" >> "$ENV_FILE"
        make_folders "$DATA_DIR"
        echo "Added WTM_DATA_DIR=$DATA_DIR to .env."
    fi
    exit 0
fi

# The data folders first, with the usual permissions (the database container has to be able to write in them);
# then the file that holds the secrets, readable by you only.
make_folders "$DATA_DIR"
(
    umask 077
    {
        echo "WTM_DATA_DIR=$DATA_DIR"
        echo "DB_USERNAME=wtm"
        echo "DB_PASSWORD=$(secret 24)"
        echo "S3_ACCESS_KEY=$(secret 12)"
        echo "S3_SECRET_KEY=$(secret 24)"
        echo "WTM_JWT_SECRET=$(secret 48)"
        echo "WTM_ADMIN_USERNAME=admin"
        echo "WTM_ADMIN_PASSWORD=$(secret 18)"
    } > "$ENV_FILE"
)
echo "Created $ENV_FILE with new random secrets, and the data folders under $DATA_DIR."
