package com.platform.common.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Clob;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class MigrationDialectCompatibilityTest {
    @Test
    void mysqlAndOceanBaseBaselineMigrationsRunAndSupportContractCrud() {
        JdbcTemplate jdbcTemplate = migrate("mysql_ob_baseline", "filesystem:../db/migration", true);

        assertContractCrud(jdbcTemplate);
        assertColumnType(jdbcTemplate, "T_API_CREDENTIAL", "ENABLED", "TINYINT");
    }

    @Test
    void damengMigrationsRunAndSupportSameContractCrud() {
        JdbcTemplate jdbcTemplate = migrate("dm_baseline", "filesystem:../db/migration-dm", false);

        assertContractCrud(jdbcTemplate);
        assertColumnType(jdbcTemplate, "T_API_CREDENTIAL", "ENABLED", "SMALLINT");
        assertColumnType(jdbcTemplate, "T_RAW_DATA", "PAYLOAD", "CHARACTER LARGE OBJECT");
    }

    @Test
    void migrationScriptsAvoidBlockedDialectFeatures() throws Exception {
        String commonVersionSql = readSql(Path.of("..", "db", "migration"), "V*.sql");
        String dmVersionSql = readSql(Path.of("..", "db", "migration-dm"), "V*.sql");

        for (String sql : List.of(commonVersionSql, dmVersionSql)) {
            assertFalse(contains(sql, "AUTO_INCREMENT"));
            assertFalse(contains(sql, "ON UPDATE CURRENT_TIMESTAMP"));
            assertFalse(contains(sql, "ON DUPLICATE KEY UPDATE"));
            assertFalse(contains(sql, "JSON_"));
            assertFalse(contains(sql, " LIMIT "));
        }
        assertFalse(contains(dmVersionSql, " TINYINT "));
        assertFalse(contains(dmVersionSql, " TEXT"));
    }

    private JdbcTemplate migrate(String name, String location, boolean mysqlMode) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource(name, mysqlMode));
        Flyway.configure()
                .dataSource(jdbcTemplate.getDataSource())
                .locations(location)
                .load()
                .migrate();
        return jdbcTemplate;
    }

    private void assertContractCrud(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("""
                INSERT INTO t_user (id, username, password_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, 1L, "contract-user", "hash", "ACTIVE");
        jdbcTemplate.update("""
                INSERT INTO t_api_credential
                (id, api_key, secret, consumer_code, service_code, enabled, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, 1L, "ak-contract", "cipher", "consumer-a", "svc-a", 1);
        jdbcTemplate.update("""
                INSERT INTO t_service_invoke_log
                (id, service_code, consumer_code, status_code, elapsed_millis, log_day, created_at, response_size)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?)
                """, 1L, "svc-a", "consumer-a", 200, 12L, "20260628", 128L);
        jdbcTemplate.update("""
                INSERT INTO t_billing_rule
                (id, rule_code, rule_name, billing_model, target_type, target_id, unit_price,
                 effective_from, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_DATE, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, 1L, "rule-a", "Rule A", "BY_COUNT", "CONSUMER", 1L, new BigDecimal("1.250000"), "ACTIVE");
        jdbcTemplate.update("""
                INSERT INTO t_bill
                (id, bill_no, bill_type, bill_period, period_start, period_end, total_amount, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, CURRENT_DATE, CURRENT_DATE, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, 1L, "BILL-A", "EXPENSE", "DAILY", new BigDecimal("1.2500"), "GENERATED");
        jdbcTemplate.update("""
                INSERT INTO t_bill_item
                (id, bill_id, bill_no, item_type, ref_id, quantity, unit_price, amount, period, consumer_code, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, 1L, 1L, "BILL-A", "CONSUMER", "consumer-a", 1L, new BigDecimal("1.250000"),
                new BigDecimal("1.2500"), "DAILY:2026-06-28:2026-06-28", "consumer-a");
        jdbcTemplate.update("""
                INSERT INTO t_raw_data
                (id, task_id, partner_id, batch_no, payload, quality_status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, 1L, 10L, 20L, "batch-a", "{\"name\":\"alpha\"}", "PASS");

        Map<String, Object> invokeLog = jdbcTemplate.queryForMap("""
                SELECT service_code, consumer_code, status_code, response_size
                FROM t_service_invoke_log
                WHERE id = ?
                """, 1L);
        assertEquals("svc-a", invokeLog.get("SERVICE_CODE"));
        assertEquals("consumer-a", invokeLog.get("CONSUMER_CODE"));
        assertEquals(200, ((Number) invokeLog.get("STATUS_CODE")).intValue());
        assertEquals(128L, ((Number) invokeLog.get("RESPONSE_SIZE")).longValue());

        jdbcTemplate.update("UPDATE t_user SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", "DISABLED", 1L);
        assertEquals("DISABLED", jdbcTemplate.queryForObject("SELECT status FROM t_user WHERE id = ?", String.class, 1L));
        assertEquals(new BigDecimal("1.2500"),
                jdbcTemplate.queryForObject("SELECT amount FROM t_bill_item WHERE id = ?", BigDecimal.class, 1L));
        assertPayload(jdbcTemplate.queryForObject("SELECT payload FROM t_raw_data WHERE id = ?", Object.class, 1L));
    }

    private void assertPayload(Object payload) {
        if (payload instanceof Clob clob) {
            try {
                assertEquals("{\"name\":\"alpha\"}", clob.getSubString(1, (int) clob.length()));
                return;
            } catch (Exception ex) {
                throw new AssertionError("Failed to read payload CLOB", ex);
            }
        }
        assertEquals("{\"name\":\"alpha\"}", String.valueOf(payload));
    }

    private void assertColumnType(JdbcTemplate jdbcTemplate, String tableName, String columnName, String expectedTypeName) {
        String actual = jdbcTemplate.queryForObject("""
                SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = ? AND COLUMN_NAME = ?
                """, String.class, tableName, columnName);
        assertEquals(expectedTypeName, actual);
    }

    private JdbcDataSource dataSource(String name, boolean mysqlMode) {
        JdbcDataSource dataSource = new JdbcDataSource();
        String mode = mysqlMode ? ";MODE=MySQL" : "";
        dataSource.setURL("jdbc:h2:mem:" + name + mode + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        return dataSource;
    }

    private String readSql(Path dir, String glob) throws Exception {
        try (var stream = Files.newDirectoryStream(dir, glob)) {
            StringBuilder sql = new StringBuilder();
            for (Path path : stream) {
                sql.append(Files.readString(path)).append('\n');
            }
            return sql.toString().toUpperCase(Locale.ROOT);
        }
    }

    private boolean contains(String sql, String pattern) {
        return sql.contains(pattern.toUpperCase(Locale.ROOT));
    }
}
