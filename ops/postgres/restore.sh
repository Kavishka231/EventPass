#!/bin/sh

SCRIPT_DIRECTORY=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
. "$SCRIPT_DIRECTORY/common.sh"

[ "$#" -eq 1 ] || die "Usage: restore.sh /path/to/backup.dump"
require_variable PGDATABASE
require_variable RESTORE_CONFIRM_DATABASE
[ "$RESTORE_CONFIRM_DATABASE" = "$PGDATABASE" ] || \
  die "RESTORE_CONFIRM_DATABASE must exactly match PGDATABASE."
require_command pg_restore

backup_file=$1
verify_backup_artifact "$backup_file"
pg_restore \
  --clean \
  --if-exists \
  --exit-on-error \
  --single-transaction \
  --no-owner \
  --no-privileges \
  --dbname="$PGDATABASE" \
  "$backup_file"
printf 'restore=completed\n'
