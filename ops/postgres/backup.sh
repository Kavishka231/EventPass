#!/bin/sh

SCRIPT_DIRECTORY=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
. "$SCRIPT_DIRECTORY/common.sh"

require_variable BACKUP_DIR
require_variable PGDATABASE
require_command pg_dump
require_command pg_restore
require_command sha256sum

BACKUP_PREFIX=${BACKUP_PREFIX:-eventpass}
RETENTION_DAYS=${RETENTION_DAYS:-30}
validate_prefix
mkdir -p -- "$BACKUP_DIR"
BACKUP_DIR=$(CDPATH= cd -- "$BACKUP_DIR" && pwd)

timestamp=$(date -u +%Y%m%dT%H%M%SZ)
backup_name="${BACKUP_PREFIX}_${timestamp}_$$.dump"
backup_file="$BACKUP_DIR/$backup_name"
temporary_dump="$BACKUP_DIR/.${backup_name}.tmp"
temporary_checksum="$BACKUP_DIR/.${backup_name}.sha256.tmp"
temporary_metadata="$BACKUP_DIR/.${backup_name}.metadata.tmp"
final_checksum="${backup_file}.sha256"
final_metadata="${backup_file}.metadata"

cleanup() {
  rm -f -- "$temporary_dump" "$temporary_checksum" "$temporary_metadata"
  if [ ! -f "$backup_file" ]; then
    rm -f -- "$final_checksum" "$final_metadata"
  fi
}
trap cleanup EXIT HUP INT TERM

pg_dump \
  --format=custom \
  --compress=9 \
  --no-owner \
  --no-privileges \
  --file="$temporary_dump" \
  "$PGDATABASE"
pg_restore --list "$temporary_dump" >/dev/null
checksum_output=$(sha256sum "$temporary_dump")
checksum=${checksum_output%% *}
printf '%s  %s\n' "$checksum" "$backup_name" >"$temporary_checksum"
{
  printf 'format=postgresql-custom\n'
  printf 'created_at=%s\n' "$timestamp"
  printf 'pg_dump_version=%s\n' "$(pg_dump --version)"
} >"$temporary_metadata"
mv -- "$temporary_checksum" "$final_checksum"
mv -- "$temporary_metadata" "$final_metadata"
mv -- "$temporary_dump" "$backup_file"

BACKUP_DIR=$BACKUP_DIR BACKUP_PREFIX=$BACKUP_PREFIX RETENTION_DAYS=$RETENTION_DAYS \
  "$SCRIPT_DIRECTORY/prune.sh"
printf '%s\n' "$backup_file"
