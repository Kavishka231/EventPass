#!/bin/sh

SCRIPT_DIRECTORY=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
. "$SCRIPT_DIRECTORY/common.sh"

require_variable BACKUP_DIR
BACKUP_PREFIX=${BACKUP_PREFIX:-eventpass}
RETENTION_DAYS=${RETENTION_DAYS:-30}
validate_prefix
case "$RETENTION_DAYS" in
  "" | *[!0-9]*) die "RETENTION_DAYS must be a positive integer." ;;
esac
[ "$RETENTION_DAYS" -ge 1 ] || die "RETENTION_DAYS must be at least 1."
[ -d "$BACKUP_DIR" ] || die "Backup directory does not exist: $BACKUP_DIR"
BACKUP_DIR=$(CDPATH= cd -- "$BACKUP_DIR" && pwd)

find "$BACKUP_DIR" -maxdepth 1 -type f -name "${BACKUP_PREFIX}_*.dump" \
  -mtime "+$RETENTION_DAYS" -print | while IFS= read -r backup_file; do
  rm -f -- "$backup_file" "${backup_file}.sha256" "${backup_file}.metadata"
  printf 'pruned=%s\n' "$backup_file"
done
