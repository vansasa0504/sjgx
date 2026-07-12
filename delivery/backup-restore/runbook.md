# P2-05 Backup Restore Runbook

## 1. Pre-check

1. Confirm maintenance window and restore target.
2. Record current deployment version and database migration version.
3. Export table counts:
   ```bash
   mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" "$DB_NAME" -e "SELECT COUNT(*) FROM t_audit_log;"
   ```
   Include `t_audit_log_archive` and `t_service_invoke_log_archive` in the recorded count comparison.
4. Confirm `/api/v1/stats/audit/verify` returns `intact=true` before backup.

## 2. Database Backup

```bash
DB_HOST=127.0.0.1 \
DB_PORT=3306 \
DB_NAME=sjgx \
DB_USER=sjgx \
DB_PASSWORD='***' \
BACKUP_DIR=/backup/sjgx/db \
bash delivery/backup-restore/backup-db.sh
```

Keep the generated `backup-YYYYMMDD-HHMMSS.sql` path in the drill record.

## 3. MinIO Backup

```bash
MINIO_ENDPOINT=http://minio:9000 \
MINIO_ACCESS_KEY='***' \
MINIO_SECRET_KEY='***' \
MINIO_BUCKET=sjgx-cold-storage \
BACKUP_TARGET=/backup/sjgx/minio/sjgx-cold-storage \
bash delivery/backup-restore/backup-minio.sh
```

## 4. Restore

Restore to a new isolated database containing none of the managed tables. The script rejects a target where any managed table already exists. Do not restore over production; use the institution database recovery procedure for in-place recovery.

```bash
DB_HOST=127.0.0.1 \
DB_PORT=3306 \
DB_NAME=sjgx_restore \
DB_USER=sjgx \
DB_PASSWORD='***' \
BACKUP_FILE=/backup/sjgx/db/backup-YYYYMMDD-HHMMSS.sql \
AUDIT_VERIFY_URL=http://platform-billing:8080/api/v1/stats/audit/verify \
AUTH_HEADER='Authorization: Bearer ***' \
bash delivery/backup-restore/restore-db.sh
```

```bash
MINIO_ENDPOINT=http://minio-restore:9000 \
MINIO_ACCESS_KEY='***' \
MINIO_SECRET_KEY='***' \
MINIO_BUCKET=sjgx-cold-storage \
BACKUP_SOURCE=/backup/sjgx/minio/sjgx-cold-storage \
bash delivery/backup-restore/restore-minio.sh
```

## 5. Post-restore Verification

1. Compare key table counts printed by `restore-db.sh` with the pre-backup record.
2. Confirm audit verify result is intact.
3. Sample lifecycle destroy records:
   ```sql
   SELECT asset_code, action, operator, reason, object_key, proof_hash
   FROM t_lifecycle_record
   WHERE action = 'DESTROY'
   ORDER BY operated_at DESC
   LIMIT 10;
   ```
4. Confirm cold objects are readable from the restored MinIO bucket.
5. Start application services against the restored target and run smoke tests.

## 6. Drill Record

Record:

- Backup file and object mirror path.
- Start/end time for backup and restore.
- RPO/RTO observed values.
- Table count comparison.
- Audit verify result.
- Lifecycle proof sample.
- Operator and approver.

## 7. Cleanup

Remove temporary restore databases and local backup copies only after the drill report is approved. Retained production backups follow institution retention and legal hold policy.
