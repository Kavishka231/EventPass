#!/bin/sh

set -eu
umask 077

die() {
  printf '%s\n' "ERROR: $*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "Required command '$1' was not found."
}

require_variable() {
  variable_name=$1
  eval "variable_value=\${$variable_name:-}"
  [ -n "$variable_value" ] || die "$variable_name must be set."
}

validate_prefix() {
  case "$BACKUP_PREFIX" in
    "" | *[!A-Za-z0-9_-]*) die "BACKUP_PREFIX may contain only letters, numbers, underscores, and hyphens." ;;
  esac
}

verify_backup_artifact() {
  backup_file=$1
  [ -f "$backup_file" ] || die "Backup file does not exist: $backup_file"
  checksum_file="${backup_file}.sha256"
  [ -f "$checksum_file" ] || die "Checksum file does not exist: $checksum_file"
  require_command sha256sum
  require_command pg_restore
  backup_directory=$(CDPATH= cd -- "$(dirname -- "$backup_file")" && pwd)
  backup_name=$(basename -- "$backup_file")
  (cd "$backup_directory" && sha256sum -c "${backup_name}.sha256")
  pg_restore --list "$backup_file" >/dev/null
}
