#!/bin/sh

SCRIPT_DIRECTORY=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
. "$SCRIPT_DIRECTORY/common.sh"

[ "$#" -eq 1 ] || die "Usage: verify.sh /path/to/backup.dump"
backup_file=$1
verify_backup_artifact "$backup_file"

VERIFY_MODE=${VERIFY_MODE:-restore}
if [ "$VERIFY_MODE" = "structural" ]; then
  printf 'verification=checksum-and-structure\n'
  exit 0
fi
[ "$VERIFY_MODE" = "restore" ] || die "VERIFY_MODE must be 'restore' or 'structural'."
require_command createdb
require_command dropdb
require_command psql

verification_database="eventpass_verify_$(date -u +%Y%m%d%H%M%S)_$$"
cleanup_verification_database() {
  dropdb --if-exists "$verification_database" >/dev/null 2>&1 || true
}
trap cleanup_verification_database EXIT HUP INT TERM

createdb "$verification_database"
pg_restore \
  --exit-on-error \
  --single-transaction \
  --no-owner \
  --no-privileges \
  --dbname="$verification_database" \
  "$backup_file"

schema_valid=$(psql --dbname="$verification_database" --no-align --tuples-only --set=ON_ERROR_STOP=1 \
  --command="SELECT to_regclass('public.flyway_schema_history') IS NOT NULL AND to_regclass('public.bookings') IS NOT NULL AND to_regclass('public.payments') IS NOT NULL AND to_regclass('public.outbox_events') IS NOT NULL;")
[ "$schema_valid" = "t" ] || die "Restored database is missing required EventPass tables."
failed_migrations=$(psql --dbname="$verification_database" --no-align --tuples-only --set=ON_ERROR_STOP=1 \
  --command="SELECT COUNT(*) FROM flyway_schema_history WHERE NOT success;")
[ "$failed_migrations" = "0" ] || die "Restored database contains failed Flyway migrations."

cleanup_verification_database
trap - EXIT HUP INT TERM
printf 'verification=full-restore\n'
