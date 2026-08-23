# PostgreSQL backup and recovery

These scripts require PostgreSQL client tools compatible with the server major version. Supply connection details through standard `PGHOST`, `PGPORT`, `PGUSER`, `PGPASSWORD`, and `PGSSLMODE` environment variables. Secrets are never accepted as command arguments or written to backup metadata.

## Backup and retention

Set an explicit protected backup directory, the source database, and retention window, then schedule `backup.sh` from a dedicated operator account:

```sh
export BACKUP_DIR=/var/backups/eventpass/postgres
export BACKUP_PREFIX=eventpass
export RETENTION_DAYS=30
export PGHOST=postgres.internal
export PGPORT=5432
export PGDATABASE=eventpass
export PGUSER=eventpass_backup
export PGPASSWORD='from-secret-store'
export PGSSLMODE=verify-full
./ops/postgres/backup.sh
```

The backup is a compressed PostgreSQL custom-format dump. It is written to a temporary file, structurally inspected, then atomically published with SHA-256 and metadata sidecars. After a successful backup, files matching this tool's prefix and older than `RETENTION_DAYS` are pruned with their sidecars. Use a database role with `CONNECT` and read access, protect the directory with operating-system permissions, encrypt backup storage, and copy completed artifacts plus sidecars to a separate failure domain. Storage lifecycle rules should be at least as strict as the local retention policy.

Run at a frequency derived from the recovery-point objective. A typical daily cron entry is:

```cron
15 2 * * * /opt/eventpass/ops/postgres/backup.sh >>/var/log/eventpass-backup.log 2>&1
```

Monitor exit status, backup age, size anomalies, free space, and off-site replication. A successful `pg_dump` alone is not proof that recovery works.

## Verification

Full verification is the default. It validates the checksum and archive structure, creates a temporary database, restores into it, checks required EventPass tables and Flyway history, and drops the temporary database:

```sh
export PGHOST=postgres-verification.internal
export PGPORT=5432
export PGUSER=eventpass_restore_verifier
export PGPASSWORD='from-secret-store'
export PGSSLMODE=verify-full
./ops/postgres/verify.sh /var/backups/eventpass/postgres/eventpass_YYYYMMDDTHHMMSSZ_PID.dump
```

The verification role needs `CREATEDB`; run verification on an isolated recovery server, not the production primary. `VERIFY_MODE=structural` performs only checksum and archive inspection when a database server is unavailable, but it does not satisfy a recovery drill. Automate full verification after backups and conduct a timed restore exercise regularly enough to validate the recovery-time objective.

## Restore runbook

Stop application writers and outbox publishers, preserve a final backup of the current target, and verify the selected artifact. Set the target connection variables and an exact confirmation value before restoring:

```sh
export PGHOST=recovery-postgres.internal
export PGPORT=5432
export PGDATABASE=eventpass_recovery
export PGUSER=eventpass_restore
export PGPASSWORD='from-secret-store'
export PGSSLMODE=verify-full
export RESTORE_CONFIRM_DATABASE=eventpass_recovery
./ops/postgres/restore.sh /secure/backups/eventpass_YYYYMMDDTHHMMSSZ_PID.dump
```

Restore uses `--clean --if-exists --single-transaction` and therefore replaces objects in the confirmed target database. It refuses to run unless `RESTORE_CONFIRM_DATABASE` exactly matches `PGDATABASE`. Afterward, run application readiness checks and domain smoke tests before enabling traffic or outbox publication. Reconcile payment outcomes and external side effects created after the backup timestamp; database restoration cannot reverse provider charges, refunds, Kafka messages, or delivered notifications.
