#!/bin/sh
# Nightly database backup for the production server (docs/deployment.md §7).
#
#   cd /opt/carlink && sh scripts/backup.sh
#
# 1. pg_dump (custom format) from the running postgres container into
#    BACKUP_DIR, readable by root only;
# 2. checks the dump is a valid archive (pg_restore --list);
# 3. keeps the newest BACKUP_KEEP dumps on the server;
# 4. if BACKUP_REMOTE is set, copies the dump off the server with rclone and
#    deletes remote dumps older than BACKUP_REMOTE_DAYS;
# 5. if BACKUP_PING_URL is set, pings it on success (e.g. a free
#    healthchecks.io check that e-mails you when no ping arrives).
#
# Settings come from the environment (all optional):
#   BACKUP_DIR=/var/backups/carlink   BACKUP_KEEP=14
#   BACKUP_REMOTE=                    e.g. b2crypt:carlink-backups (an rclone crypt remote)
#   BACKUP_REMOTE_DAYS=30             BACKUP_PING_URL=
#   COMPOSE_FILE_PATH=docker-compose.prod.yml   ENV_FILE=.env.prod
#
# The dump does NOT contain PHONE_ENCRYPTION_KEY (it lives in .env.prod): keep
# that key in your password manager, never next to the dumps.
set -eu

BACKUP_DIR="${BACKUP_DIR:-/var/backups/carlink}"
BACKUP_KEEP="${BACKUP_KEEP:-14}"
BACKUP_REMOTE="${BACKUP_REMOTE:-}"
BACKUP_REMOTE_DAYS="${BACKUP_REMOTE_DAYS:-30}"
BACKUP_PING_URL="${BACKUP_PING_URL:-}"
COMPOSE_FILE_PATH="${COMPOSE_FILE_PATH:-docker-compose.prod.yml}"
ENV_FILE="${ENV_FILE:-.env.prod}"

log() { echo "$(date '+%Y-%m-%d %H:%M:%S') backup: $*"; }
fail() { log "FAILED: $*"; exit 1; }

cd "$(dirname "$0")/.."
[ -f "$COMPOSE_FILE_PATH" ] || fail "$COMPOSE_FILE_PATH not found"
[ -f "$ENV_FILE" ] || fail "$ENV_FILE not found"

compose() { docker compose -f "$COMPOSE_FILE_PATH" --env-file "$ENV_FILE" "$@"; }

umask 077
mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"

name="carlink-$(date '+%Y-%m-%d_%H%M').dump"
tmp="$BACKUP_DIR/.$name.partial"
trap 'rm -f "$tmp"' EXIT

# Database and user are read from the container's own environment.
compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB"' > "$tmp" \
  || fail "pg_dump did not complete"

# A truncated or empty file is not a backup.
entries="$(compose exec -T postgres pg_restore --list < "$tmp" | grep -c ' TABLE ' || true)"
[ "${entries:-0}" -gt 0 ] || fail "dump has no tables (pg_restore --list)"
mv "$tmp" "$BACKUP_DIR/$name"
log "wrote $BACKUP_DIR/$name ($(du -h "$BACKUP_DIR/$name" | cut -f1), $entries tables)"

# Keep the newest BACKUP_KEEP dumps (names sort by date).
ls -1 "$BACKUP_DIR"/carlink-*.dump 2>/dev/null | sort -r | tail -n "+$((BACKUP_KEEP + 1))" | while read -r old; do
  rm -f "$old"
  log "removed old $old"
done

if [ -n "$BACKUP_REMOTE" ]; then
  command -v rclone >/dev/null 2>&1 || fail "BACKUP_REMOTE is set but rclone is not installed"
  rclone copy "$BACKUP_DIR/$name" "$BACKUP_REMOTE" || fail "off-site copy to $BACKUP_REMOTE"
  rclone delete --min-age "${BACKUP_REMOTE_DAYS}d" --include 'carlink-*.dump' "$BACKUP_REMOTE" \
    || log "warning: could not prune old remote dumps"
  log "copied off-site to $BACKUP_REMOTE"
fi

if [ -n "$BACKUP_PING_URL" ]; then
  curl -fsS -m 10 --retry 3 -o /dev/null "$BACKUP_PING_URL" || log "warning: ping failed"
fi
log "done"
