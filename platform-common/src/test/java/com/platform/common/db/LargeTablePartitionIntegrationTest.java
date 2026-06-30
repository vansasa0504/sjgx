package com.platform.common.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;

class LargeTablePartitionIntegrationTest {
    @Test
    void rangeQueriesPruneMonthlyPartitionsAndUseIndexes() {
        assumeTrue(Boolean.getBoolean("runPartitionIT"), "set -DrunPartitionIT=true to run MySQL partition EXPLAIN test");
        try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.36")
                .withDatabaseName("sjgx")
                .withUsername("sjgx")
                .withPassword("sjgx")) {
            mysql.start();
            assertPartitions(mysql);
        }
    }

    private void assertPartitions(MySQLContainer<?> mysql) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(mysql.getJdbcUrl());
        dataSource.setUsername(mysql.getUsername());
        dataSource.setPassword(mysql.getPassword());
        dataSource.setDriverClassName(mysql.getDriverClassName());
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Flyway.configure()
                .dataSource(jdbcTemplate.getDataSource())
                .locations("filesystem:" + Path.of("..", "db", "migration").toAbsolutePath())
                .load()
                .migrate();

        insertInvokeLog(jdbcTemplate, 1L, "2026-01-10T00:00:00Z");
        insertInvokeLog(jdbcTemplate, 2L, "2026-02-10T00:00:00Z");
        insertInvokeLog(jdbcTemplate, 3L, "2026-03-10T00:00:00Z");
        insertAuditLog(jdbcTemplate, 1L, "2026-01-10T00:00:00Z");
        insertAuditLog(jdbcTemplate, 2L, "2026-02-10T00:00:00Z");
        insertRawData(jdbcTemplate, 1L, "2026-01-10T00:00:00Z");
        insertRawData(jdbcTemplate, 2L, "2026-02-10T00:00:00Z");

        assertExplain(jdbcTemplate, "t_service_invoke_log", "idx_invoke_log_created_at", "p202602");
        assertExplain(jdbcTemplate, "t_audit_log", "idx_audit_log_created_at", "p202602");
        assertPartition(jdbcTemplate, "t_raw_data", "p202602");
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM t_service_invoke_log
                WHERE created_at >= '2026-02-01' AND created_at < '2026-03-01'
                """, Integer.class));
    }

    private void assertExplain(JdbcTemplate jdbcTemplate, String table, String expectedKey, String expectedPartition) {
        Map<String, Object> plan = jdbcTemplate.queryForMap("EXPLAIN SELECT * FROM " + table
                + " WHERE created_at >= '2026-02-01' AND created_at < '2026-03-01'");
        System.out.printf("EXPLAIN %s partitions=%s key=%s rows=%s%n",
                table, plan.get("partitions"), plan.get("key"), plan.get("rows"));
        assertEquals(expectedPartition, String.valueOf(plan.get("partitions")));
        assertNotNull(plan.get("key"), "expected index for " + table + ": " + plan);
        assertTrue(String.valueOf(plan.get("key")).contains(expectedKey), "expected key " + expectedKey + ": " + plan);
    }

    private void assertPartition(JdbcTemplate jdbcTemplate, String table, String expectedPartition) {
        Map<String, Object> plan = jdbcTemplate.queryForMap("EXPLAIN SELECT * FROM " + table
                + " WHERE created_at >= '2026-02-01' AND created_at < '2026-03-01'");
        System.out.printf("EXPLAIN %s partitions=%s key=%s rows=%s%n",
                table, plan.get("partitions"), plan.get("key"), plan.get("rows"));
        assertEquals(expectedPartition, String.valueOf(plan.get("partitions")));
    }

    private void insertInvokeLog(JdbcTemplate jdbcTemplate, long id, String createdAt) {
        Instant instant = Instant.parse(createdAt);
        jdbcTemplate.update("""
                INSERT INTO t_service_invoke_log
                (id, trace_id, service_code, consumer_code, status_code, elapsed_millis, log_day, created_at, response_size)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, "trace-" + id, "svc", "consumer", 200, 10L, "202602", Timestamp.from(instant), 128L);
    }

    private void insertAuditLog(JdbcTemplate jdbcTemplate, long id, String createdAt) {
        jdbcTemplate.update("""
                INSERT INTO t_audit_log
                (id, trace_id, event_type, actor_type, actor_id, target_type, target_id, action,
                 detail, status, created_at, prev_hash, hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, "audit-" + id, "TEST", "SYSTEM", "system", "TABLE", "t", "TEST",
                "detail", "SUCCESS", Timestamp.from(Instant.parse(createdAt)), "", "hash-" + id);
    }

    private void insertRawData(JdbcTemplate jdbcTemplate, long id, String createdAt) {
        jdbcTemplate.update("""
                INSERT INTO t_raw_data
                (id, task_id, partner_id, batch_no, payload, quality_status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, 1L, 1L, "batch", "{\"name\":\"alpha\"}", "PASS", Timestamp.from(Instant.parse(createdAt)));
    }
}
