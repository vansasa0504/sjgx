package com.platform.common.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@EnabledIfEnvironmentVariable(named = "RUN_BACKUP_RESTORE_IT", matches = "true")
class BackupRestoreAuditChainIT {
    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("sjgx")
            .withUsername("sjgx")
            .withPassword("sjgx");

    @TempDir
    Path tempDir;

    @Test
    void backupRestoreKeepsAuditHashChainIntactAcrossVerifyBatches() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        Flyway.configure()
                .dataSource(jdbcTemplate.getDataSource())
                .locations("filesystem:" + Path.of("..", "db", "migration").toAbsolutePath().normalize())
                .load()
                .migrate();
        JdbcAuditLogRepository repository = new JdbcAuditLogRepository(jdbcTemplate);
        for (int i = 0; i < 2505; i++) {
            repository.append(new AuditEvent(null, "trace-backup", "BACKUP_RESTORE", "SYSTEM", "it",
                    "AUDIT", "event-" + i, "append", "detail-" + i, "", "",
                    AuditStatus.SUCCESS, Instant.parse("2026-07-01T00:00:00Z").plusSeconds(i)));
        }
        long beforeCount = count(jdbcTemplate);
        assertEquals(2505L, beforeCount);
        assertTrue(repository.verify().intact());

        Path backupFile = tempDir.resolve("audit-backup.tsv");
        exportAuditLog(jdbcTemplate, backupFile);
        jdbcTemplate.update("DELETE FROM t_audit_log");
        assertEquals(0L, count(jdbcTemplate));

        restoreAuditLog(jdbcTemplate, backupFile);

        assertEquals(beforeCount, count(jdbcTemplate));
        AuditChainVerification verification = repository.verify();
        assertTrue(verification.intact(), verification.toString());
        assertEquals(beforeCount, verification.totalChecked());
    }

    private JdbcTemplate jdbcTemplate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(mysql.getDriverClassName());
        dataSource.setUrl(mysql.getJdbcUrl());
        dataSource.setUsername(mysql.getUsername());
        dataSource.setPassword(mysql.getPassword());
        return new JdbcTemplate(dataSource);
    }

    private long count(JdbcTemplate jdbcTemplate) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_audit_log", Long.class);
        return count == null ? 0L : count;
    }

    private void exportAuditLog(JdbcTemplate jdbcTemplate, Path backupFile) throws Exception {
        List<String> lines = jdbcTemplate.query("""
                SELECT id, trace_id, event_type, actor_type, actor_id, target_type, target_id, action,
                       detail, source_ip, user_agent, status, created_at, prev_hash, hash
                FROM t_audit_log
                ORDER BY id
                """, (rs, rowNum) -> String.join("\t",
                String.valueOf(rs.getLong("id")),
                encode(rs.getString("trace_id")),
                encode(rs.getString("event_type")),
                encode(rs.getString("actor_type")),
                encode(rs.getString("actor_id")),
                encode(rs.getString("target_type")),
                encode(rs.getString("target_id")),
                encode(rs.getString("action")),
                encode(rs.getString("detail")),
                encode(rs.getString("source_ip")),
                encode(rs.getString("user_agent")),
                encode(rs.getString("status")),
                String.valueOf(rs.getTimestamp("created_at").toInstant().toEpochMilli()),
                encode(rs.getString("prev_hash")),
                encode(rs.getString("hash"))));
        Files.write(backupFile, lines, StandardCharsets.UTF_8);
    }

    private void restoreAuditLog(JdbcTemplate jdbcTemplate, Path backupFile) throws Exception {
        List<Object[]> batch = new ArrayList<>();
        for (String line : Files.readAllLines(backupFile, StandardCharsets.UTF_8)) {
            String[] parts = line.split("\t", -1);
            batch.add(new Object[] {
                    Long.parseLong(parts[0]), decode(parts[1]), decode(parts[2]), decode(parts[3]), decode(parts[4]),
                    decode(parts[5]), decode(parts[6]), decode(parts[7]), decode(parts[8]), decode(parts[9]),
                    decode(parts[10]), decode(parts[11]), Timestamp.from(Instant.ofEpochMilli(Long.parseLong(parts[12]))),
                    decode(parts[13]), decode(parts[14])
            });
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO t_audit_log
                (id, trace_id, event_type, actor_type, actor_id, target_type, target_id, action,
                 detail, source_ip, user_agent, status, created_at, prev_hash, hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, batch);
    }

    private String encode(String value) {
        return Base64.getEncoder().encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
