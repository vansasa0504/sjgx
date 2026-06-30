# P2-01 large table partition EXPLAIN evidence

Date: 2026-06-30

## Command

```bash
mvn test -pl platform-common -Dtest=LargeTablePartitionIntegrationTest -DrunPartitionIT=true
```

## Environment

- MySQL: `mysql:8.0.36` Testcontainers
- Migration path: `db/migration` through `V022__large_table_partition.sql`
- Partition strategy: monthly `RANGE COLUMNS (created_at)`

## EXPLAIN result

The integration test inserted January, February, and March rows, then queried:

```sql
EXPLAIN SELECT * FROM <table>
WHERE created_at >= '2026-02-01' AND created_at < '2026-03-01';
```

Observed output:

```text
EXPLAIN t_service_invoke_log partitions=p202602 key=idx_invoke_log_created_at rows=1
EXPLAIN t_audit_log partitions=p202602 key=idx_audit_log_created_at rows=1
EXPLAIN t_raw_data partitions=p202602 key=null rows=1
```

## Notes

- `t_service_invoke_log` and `t_audit_log` verify both monthly partition pruning and range index usage.
- `t_raw_data` verifies monthly partition pruning only. P2-01 explicitly does not add a new `t_raw_data.created_at` index.
- The MySQL migration changes `created_at` to `DATETIME(6)` for partitioned tables because MySQL 8.0 rejects both `TO_DAYS(TIMESTAMP)` range partitioning and `RANGE COLUMNS` over `TIMESTAMP`.
- Current billing/statistics aggregation still materializes the selected billing window through `findAllByRange`. This removes full-table scans but can still be memory-heavy for very large billing periods. A follow-up production hardening task should move those aggregations to SQL `GROUP BY`/streaming aggregation.
