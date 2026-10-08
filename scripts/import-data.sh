#!/usr/bin/env bash
# Loads an export made by scripts/export-data.sh into this computer's database and picture store.
#
#   scripts/import-data.sh /path/to/wtm-export-20261009-120000
#
# Do this on a fresh install: .env created (scripts/init-env.sh), the two containers running (docker compose up -d),
# and the application NOT started yet. It refuses to touch a database that already holds memes, unless you say --force.
#
# The users come along with the dump, and so do their password hashes: you sign in on the new computer with the
# password the administrator had on the old one, not with the WTM_ADMIN_PASSWORD of the new .env (which only matters
# when there is no administrator yet).
set -euo pipefail
export MSYS_NO_PATHCONV=1

FORCE=no
if [ "${1:-}" = "--force" ]; then FORCE=yes; shift; fi
SRC="${1:-}"
[ -n "$SRC" ] && [ -f "$SRC/db.dump" ] && [ -d "$SRC/images" ] || {
    echo "Usage: scripts/import-data.sh [--force] <export folder made by export-data.sh>" >&2; exit 1; }

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT/.env"
[ -f "$ENV_FILE" ] || { echo "No .env found. Run scripts/init-env.sh first." >&2; exit 1; }
setting() { grep -E "^$1=" "$ENV_FILE" | head -1 | cut -d= -f2- | tr -d '\r'; }
manifest() { grep -E "^$1=" "$SRC/MANIFEST.txt" 2>/dev/null | cut -d= -f2- | tr -d '\r'; }

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
SRC="$(cd "$SRC" && pwd)"

wait_for_database
NETWORK="$(docker inspect "$S3_CONTAINER" --format '{{range $k,$v := .NetworkSettings.Networks}}{{$k}} {{end}}' | awk '{print $1}')"
[ -n "$NETWORK" ] || { echo "The storage container '$S3_CONTAINER' is not running." >&2; exit 1; }
sql() { docker exec "$PG_CONTAINER" psql -U "$DB_USER" -d wtm -t -A -c "$1"; }

# A database that already has memes is somebody's work: do not overwrite it by accident.
HAS_TABLE="$(sql "select count(*) from information_schema.tables where table_schema='public' and table_name='meme_template'")"
if [ "$HAS_TABLE" = "0" ]; then EXISTING=0; else EXISTING="$(sql 'select count(*) from meme_template')"; fi
if [ "$EXISTING" != "0" ] && [ "$FORCE" != "yes" ]; then
    echo "This database already holds $EXISTING memes. Importing would replace them." >&2
    echo "If that is what you want, run again with --force." >&2
    exit 1
fi

# Check the dump is the one that was exported.
EXPECTED="$(manifest db_dump_sha256)"
if [ -n "$EXPECTED" ]; then
    if command -v sha256sum >/dev/null 2>&1; then ACTUAL="$(sha256sum "$SRC/db.dump" | cut -d' ' -f1)"; else ACTUAL="$(shasum -a 256 "$SRC/db.dump" | cut -d' ' -f1)"; fi
    [ "$EXPECTED" = "$ACTUAL" ] || { echo "db.dump does not match MANIFEST.txt: the copy is damaged. Copy the folder again." >&2; exit 1; }
fi

echo "1/3 database ..."
docker exec -i "$PG_CONTAINER" pg_restore -U "$DB_USER" -d wtm --clean --if-exists --no-owner --no-privileges --exit-on-error < "$SRC/db.dump"

echo "2/3 pictures ..."
AWS_ACCESS_KEY_ID="$(setting S3_ACCESS_KEY)"; AWS_SECRET_ACCESS_KEY="$(setting S3_SECRET_KEY)"
export AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY
aws() {
    docker run --rm --network "$NETWORK" -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_DEFAULT_REGION=us-east-1 \
        -v "$SRC/images:/data" amazon/aws-cli --endpoint-url "http://$S3_CONTAINER:9000" "$@"
}
aws s3api head-bucket --bucket "$BUCKET" >/dev/null 2>&1 || aws s3 mb "s3://$BUCKET" >/dev/null
aws s3 sync /data "s3://$BUCKET" --no-progress --only-show-errors

echo "3/3 checking ..."
MEMES="$(sql 'select count(*) from meme_template')"
INDEXED="$(sql 'select count(*) from template_search')"
STORED="$(aws s3 ls "s3://$BUCKET" --recursive | wc -l | tr -d ' ')"
echo "memes in the database : $MEMES   (exported: $(manifest meme_rows))"
echo "search index entries  : $INDEXED"
echo "pictures in the store : $STORED   (exported: $(manifest picture_files))"
if [ "$MEMES" != "$(manifest meme_rows)" ] || [ "$STORED" -lt "$(manifest picture_files)" ]; then
    echo "WARNING: the numbers above do not match the export." >&2
    exit 2
fi
echo
echo "Done. Start the application as usual (mvn spring-boot:run) and the web page (cd web && npm run dev)."
