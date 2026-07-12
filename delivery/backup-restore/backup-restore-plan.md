# P2-05 Backup Restore Plan

## Scope

This plan covers development-verifiable backup and restore controls for database facts, audit hash-chain verification, MinIO cold objects, Redis rebuild posture, and lifecycle destroy proof evidence. It does not replace the production institution backup platform, offsite DR, or database vendor physical backup plan.

## Database Strategy

- Development script: `delivery/backup-restore/backup-db.sh` uses `mysqldump --single-transaction --routines --no-tablespaces` for key tables.
- Restore script: `delivery/backup-restore/restore-db.sh` restores a dump, prints key table counts, and can call `/api/v1/stats/audit/verify` through `AUDIT_VERIFY_URL`.
- Restore safety: the logical restore accepts only an isolated database that contains none of the managed tables; it fails before import when a target table already exists.
- Production strategy: use the institution database physical backup or snapshot plan for Dameng/OceanBase, with logical dumps retained as an application-level drill and emergency export option.
- Frequency: full backup daily, incremental/binlog or vendor delta backup every 5 minutes where supported, aligned to RPO <= 5 minutes.
- Retention: audit and service invoke logs >= 3 years; bills follow financial retention policy; raw data follows contract and data classification policy.

Key backup tables:

- `t_audit_log`
- `t_audit_log_archive`
- `t_service_invoke_log`
- `t_service_invoke_log_archive`
- `t_raw_data`
- `t_bill`
- `t_bill_item`
- `t_finance_sync_record`
- `t_data_catalog`
- `t_user`
- `t_api_credential`
- `t_lifecycle_record`

## Redis Strategy

Redis stores cache, quota counters, offsets, and transient locks. Authoritative facts remain in database tables. During Redis outage P2-03 fallback keeps core service available with local quota precision degradation. Recovery steps are:

1. Restore Redis from production RDB/AOF if the institution requires exact cache continuity.
2. Otherwise rebuild cache from database and reset quota windows from DB snapshots or the next billing/quota period.
3. Confirm application logs switch back from fallback to Redis-backed counters.

Redis full rebuild automation is not in P2-05 scope and remains a production hardening item.

## MinIO Strategy

- Development script: `backup-minio.sh` mirrors the cold-storage bucket with `mc mirror` and enables bucket versioning when available.
- Restore script: `restore-minio.sh` recreates the bucket if needed and mirrors objects back.
- Production strategy: enable bucket versioning, lifecycle policy, object lock where required, and offsite replication.

## Audit Verification

After every restore drill:

1. Compare counts for key tables before backup and after restore.
2. Call `GET /api/v1/stats/audit/verify` or run `JdbcAuditLogRepository.verify()` in the recovery test harness.
3. Verify lifecycle destroy evidence in `t_lifecycle_record`: `operator`, `reason`, `object_key`, and `proof_hash` must be present for new destroy records.
4. Store the restore report with backup file name, restored timestamp, count evidence, audit verification result, and operator.

P2-05 adds a gated Testcontainers IT that verifies `backup -> delete -> restore -> audit hash-chain intact` for more than 2000 audit rows.

The `DataLifecycleManager` destroy-proof persistence capability is implemented and unit tested, but it is not yet wired into a production lifecycle destroy entry point. Production destroy-record persistence remains a follow-up integration item.

## NFR Mapping

| Requirement | Control | Current Evidence |
|---|---|---|
| NFR-A03 data zero loss | DB backup/restore scripts plus restore count checks | Development closed-loop IT, production drill pending |
| NFR-A04 RPO <= 5min, RTO <= 30min | Production vendor backup, binlog/delta backup, offsite replication | Runbook defined, production timing drill pending |
| NFR-S02 audit >= 3 years | P2-01 archive mechanism, append-only audit hash chain, restore verify | Verify endpoint and backup-restore IT; production retention switch pending |

## Known Limits

- Development verification uses Testcontainers and JDBC export fallback; production should prefer vendor physical backup/snapshot.
- Real offsite DR and RPO/RTO timing depend on production infrastructure.
- Redis exact rebuild is not implemented in this task.
- `t_raw_data` restore is covered by script/table count but full read-path verification remains a separate gap.
