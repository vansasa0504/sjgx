package com.platform.common.partition;

import com.platform.common.audit.AuditEvent;
import com.platform.common.audit.AuditLogRepository;
import com.platform.common.audit.AuditStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcPartitionMaintainer implements PartitionMaintainer {
    private static final Map<String, String> ARCHIVE_TABLES = Map.of(
            "t_service_invoke_log", "t_service_invoke_log_archive",
            "t_audit_log", "t_audit_log_archive");

    private final JdbcTemplate jdbcTemplate;
    private final AuditLogRepository auditLogRepository;

    public JdbcPartitionMaintainer(JdbcTemplate jdbcTemplate, AuditLogRepository auditLogRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public void ensureFuturePartitions(String table, int monthsAhead) {
        requireAllowed(table);
        YearMonth start = YearMonth.now(ZoneOffset.UTC);
        int created = 0;
        for (int i = 0; i <= Math.max(0, monthsAhead); i++) {
            YearMonth month = start.plusMonths(i);
            String partition = partitionName(month);
            if (!partitionExists(table, partition)) {
                YearMonth next = month.plusMonths(1);
                execute("""
                        ALTER TABLE %s REORGANIZE PARTITION pmax INTO (
                            PARTITION %s VALUES LESS THAN ('%s-01'),
                            PARTITION pmax VALUES LESS THAN MAXVALUE
                        )
                        """.formatted(table, partition, next));
                created++;
            }
        }
        appendAudit(table, "ENSURE", "monthsAhead=" + monthsAhead + ",created=" + created);
    }

    @Override
    public void archiveExpiredPartitions(String table, Instant cutoff) {
        String archiveTable = requireArchivable(table);
        int rows = jdbcTemplate.update("""
                INSERT INTO %s
                SELECT source.*
                FROM %s source
                WHERE source.created_at < ?
                  AND NOT EXISTS (
                      SELECT 1
                      FROM %s archive
                      WHERE archive.id = source.id
                  )
                """.formatted(archiveTable, table, archiveTable), Timestamp.from(cutoff));
        appendAudit(table, "ARCHIVE", "archiveTable=" + archiveTable + ",cutoff=" + cutoff + ",rows=" + rows);
    }

    @Override
    public void dropExpiredPartitions(String table, Instant cutoff) {
        requireAllowed(table);
        YearMonth cutoffMonth = YearMonth.from(cutoff.atZone(ZoneOffset.UTC));
        int dropped = 0;
        for (String partition : partitions(table)) {
            if (partition == null || "pmax".equalsIgnoreCase(partition) || !partition.startsWith("p")) {
                continue;
            }
            YearMonth month = YearMonth.parse(partition.substring(1), java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
            if (month.isBefore(cutoffMonth)) {
                execute("ALTER TABLE " + table + " DROP PARTITION " + partition);
                dropped++;
            }
        }
        appendAudit(table, "DROP", "cutoff=" + cutoff + ",dropped=" + dropped);
    }

    protected void execute(String sql) {
        jdbcTemplate.execute(sql);
    }

    protected java.util.List<String> partitions(String table) {
        return jdbcTemplate.queryForList("""
                SELECT PARTITION_NAME
                FROM INFORMATION_SCHEMA.PARTITIONS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
                """, String.class, table);
    }

    private boolean partitionExists(String table, String partition) {
        return partitions(table).stream().anyMatch(partition::equalsIgnoreCase);
    }

    private String requireArchivable(String table) {
        requireAllowed(table);
        String archive = ARCHIVE_TABLES.get(table);
        if (archive == null) {
            throw new IllegalArgumentException("table is not archivable: " + table);
        }
        return archive;
    }

    private void requireAllowed(String table) {
        if (!ARCHIVE_TABLES.containsKey(table) && !"t_raw_data".equals(table)) {
            throw new IllegalArgumentException("unsupported partition table: " + table);
        }
    }

    private String partitionName(YearMonth month) {
        return "p" + month.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
    }

    private void appendAudit(String table, String action, String detail) {
        auditLogRepository.append(new AuditEvent(null, UUID.randomUUID().toString(), "PARTITION_MAINTAIN",
                "SYSTEM", "system", "TABLE", table, action, detail, "", "", AuditStatus.SUCCESS, Instant.now()));
    }
}
