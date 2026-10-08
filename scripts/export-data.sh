#!/usr/bin/env bash
# Exports everything the site holds into one folder you can copy to another computer, or keep as a backup:
#
#   db.dump        the database (pg_dump, custom format): memes, descriptions, users, favourites, reports, search index
#   images/        every picture, as the plain files they are (library/<id>.jpg ...)
#   MANIFEST.txt   what was exported, with counts and a checksum, so an import can be checked
#
# Why not simply copy the data folder: the database files of one kind of processor (Intel, AMD) cannot be read by another
# (Apple silicon), and the picture store is in the storage server's own layout. A dump and plain files work everywhere.
#
# Needs the two containers running (docker compose up -d). The application does not have to be stopped.
#
#   scripts/export-data.sh                 into <data folder>/backups/wtm-export-<date>/
#   scripts/export-data.sh /some/folder    into /some/folder/wtm-export-<date>/
set -euo pipefail
export MSYS_NO_PATHCONV=1   # Git Bash on Windows would rewrite the container paths below

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT/.env"
[ -f "$ENV_FILE" ] || { echo "No .env found. Run scripts/init-env.sh (or init-env.ps1) first." >&2; exit 1; }

# The value of a setting in .env, without printing anything else from it.
setting() { grep -E "^$1=" "$ENV_FILE" | head -1 | cut -d= -f2- | tr -d '\r'; }

PG_CONTAINER="${PG_CONTAINER:-wtm-postgres}"
S3_CONTAINER="${S3_CONTAINER:-wtm-object-storage}"
BUCKET="${WTM_BUCKET:-wtm}"
DB_USER="$(setting DB_USERNAME)"; DB_USER="${DB_USER:-wtm}"

# A freshly created database container answers for a few seconds, shuts down and starts again while it sets itself up.
# Wait until it has answered twice, a few seconds apart.
wait_for_database() {
    docker inspect "$PG_CONTAINER" >/dev/null 2>&1         || { echo "The database container '$PG_CONTAINER' is not running. Start it with: docker compose up -d" >&2; exit 1; }
    ok=0; tries=0
    while [ "$ok" -lt 2 ]; do
        if docker exec "$PG_CONTAINER" psql -U "$DB_USER" -d wtm -t -A -c 'select 1' >/dev/null 2>&1; then
            ok=$((ok + 1)); sleep 3
        else
            ok=0; sleep 2
        fi
        tries=$((tries + 1))
        [ "$tries" -lt 60 ] || { echo "The database '$PG_CONTAINER' did not become ready." >&2; exit 1; }
    done
}
BASE="${1:-$(setting WTM_DATA_DIR)/backups}"
STAMP="$(date +%Y%m%d-%H%M%S)"
DEST="$BASE/wtm-export-$STAMP"

wait_for_database

# The pictures are read through the storage server's own S3 interface, from a throw-away container on its network.
NETWORK="$(docker inspect "$S3_CONTAINER" --format '{{range $k,$v := .NetworkSettings.Networks}}{{$k}} {{end}}' | awk '{print $1}')"
[ -n "$NETWORK" ] || { echo "The storage container '$S3_CONTAINER' is not running." >&2; exit 1; }
AWS_ACCESS_KEY_ID="$(setting S3_ACCESS_KEY)"; AWS_SECRET_ACCESS_KEY="$(setting S3_SECRET_KEY)"
export AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY   # passed on by name below, so the values never appear on a command line

mkdir -p "$DEST/images"
echo "Exporting into $DEST"

echo "1/3 database ..."
docker exec "$PG_CONTAINER" pg_dump -U "$DB_USER" -d wtm -Fc --no-owner --no-privileges > "$DEST/db.dump"

echo "2/3 pictures ..."
docker run --rm --network "$NETWORK" -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_DEFAULT_REGION=us-east-1 \
    -v "$DEST/images:/data" amazon/aws-cli \
    --endpoint-url "http://$S3_CONTAINER:9000" s3 sync "s3://$BUCKET" /data --no-progress --only-show-errors

echo "3/3 checking ..."
sql() { docker exec "$PG_CONTAINER" psql -U "$DB_USER" -d wtm -t -A -c "$1"; }
MEMES="$(sql 'select count(*) from meme_template')"
WITH_PICTURE="$(sql "select count(*) from meme_template where image_key is not null")"
FILES="$(find "$DEST/images" -type f | wc -l | tr -d ' ')"
SCHEMA="$(sql 'select max(version::int) from flyway_schema_history where success')"
if command -v sha256sum >/dev/null 2>&1; then SUM="$(sha256sum "$DEST/db.dump" | cut -d' ' -f1)"; else SUM="$(shasum -a 256 "$DEST/db.dump" | cut -d' ' -f1)"; fi
SIZE="$(du -sh "$DEST" | cut -f1)"

cat > "$DEST/MANIFEST.txt" <<EOF
exported_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
git_commit=$( (cd "$ROOT" && git rev-parse --short HEAD 2>/dev/null) || echo unknown)
schema_version=$SCHEMA
meme_rows=$MEMES
rows_with_picture=$WITH_PICTURE
picture_files=$FILES
db_dump_sha256=$SUM
EOF

echo
cat "$DEST/MANIFEST.txt"
echo "total size: $SIZE"
if [ "$WITH_PICTURE" != "$FILES" ]; then
    echo "WARNING: $WITH_PICTURE entries have a picture but $FILES picture files were exported." >&2
    echo "Do not rely on this export until the difference is explained." >&2
    exit 2
fi
echo
echo "Done. Copy the whole folder $DEST to the other computer (AirDrop, an external drive, a cloud drive),"
echo "then run scripts/import-data.sh there. See docs/macos.md."
