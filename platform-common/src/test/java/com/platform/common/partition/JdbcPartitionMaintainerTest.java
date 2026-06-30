package com.platform.common.partition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.platform.common.audit.InMemoryAuditLogRepository;
import java.sql.Timestamp;
import java.time.Instant;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcPartitionMaintainerTest {
    @Test
    void archivesExpiredRowsAndWritesAudit() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
        createTables(jdbcTemplate);
        InMemoryAuditLogRepository audit = new InMemoryAuditLogRepository();
        JdbcPartitionMaintainer maintainer = new JdbcPartitionMaintainer(jdbcTemplate, audit);
        Instant old = Instant.parse("2026-01-01T00:00:00Z");
        Instant current = Instant.parse("2026-03-01T00:00:00Z");
        insertInvokeLog(jdbcTemplate, 1L, old);
        insertInvokeLog(jdbcTemplate, 2L, current);

        maintainer.archiveExpiredPartitions("t_service_invoke_log", Instant.parse("2026-02-01T00:00:00Z"));

        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_service_invoke_log_archive", Integer.class));
        assertEquals(1, audit.findByEventType("PARTITION_MAINTAIN", Instant.EPOCH, Instant.now()).size());
    }

    @Test
    void archiveIsIdempotentWhenRetried() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
        createTables(jdbcTemplate);
        JdbcPartitionMaintainer maintainer = new JdbcPartitionMaintainer(jdbcTemplate, new InMemoryAuditLogRepository());
        insertInvokeLog(jdbcTemplate, 1L, Instant.parse("2026-01-01T00:00:00Z"));

        maintainer.archiveExpiredPartitions("t_service_invoke_log", Instant.parse("2026-02-01T00:00:00Z"));
        maintainer.archiveExpiredPartitions("t_service_invoke_log", Instant.parse("2026-02-01T00:00:00Z"));

        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_service_invoke_log_archive", Integer.class));
    }

    @Test
    void rejectsUnsupportedTableName() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
        JdbcPartitionMaintainer maintainer = new JdbcPartitionMaintainer(jdbcTemplate, new InMemoryAuditLogRepository());

        assertThrows(IllegalArgumentException.class,
                () -> maintainer.archiveExpiredPartitions("t_service_invoke_log;DROP TABLE t_user", Instant.now()));
    }

    @Test
    void ensureAndDropCanBeExercisedWithRecordedSql() {
        RecordingMaintainer maintainer = new RecordingMaintainer();

        maintainer.ensureFuturePartitions("t_service_invoke_log", 0);
        maintainer.dropExpiredPartitions("t_service_invoke_log", Instant.parse("2026-02-01T00:00:00Z"));

        assertTrue(maintainer.sql.toString().contains("REORGANIZE PARTITION pmax"));
        assertTrue(maintainer.sql.toString().contains("DROP PARTITION p202601"));
    }

    private void createTables(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE t_service_invoke_log (
                    id BIGINT PRIMARY KEY,
                    trace_id VARCHAR(64),
                    service_code VARCHAR(64) NOT NULL,
                    consumer_code VARCHAR(64) NOT NULL,
                    partner_code VARCHAR(64),
                    api_key VARCHAR(128),
                    request_hash VARCHAR(128),
                    status_code INT NOT NULL,
                    elapsed_millis BIGINT NOT NULL,
                    response_size BIGINT DEFAULT 0 NOT NULL,
                    error_code VARCHAR(64),
                    error_message VARCHAR(512),
                    log_day VARCHAR(8) NOT NULL,
                    created_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_service_invoke_log_archive (
                    id BIGINT NOT NULL,
                    trace_id VARCHAR(64),
                    service_code VARCHAR(64) NOT NULL,
                    consumer_code VARCHAR(64) NOT NULL,
                    partner_code VARCHAR(64),
                    api_key VARCHAR(128),
                    request_hash VARCHAR(128),
                    status_code INT NOT NULL,
                    elapsed_millis BIGINT NOT NULL,
                    response_size BIGINT DEFAULT 0 NOT NULL,
                    error_code VARCHAR(64),
                    error_message VARCHAR(512),
                    log_day VARCHAR(8) NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    PRIMARY KEY (id, created_at)
                )
                """);
        jdbcTemplate.execute("CREATE UNIQUE INDEX uk_invoke_log_archive_id ON t_service_invoke_log_archive(id)");
    }

    private void insertInvokeLog(JdbcTemplate jdbcTemplate, long id, Instant createdAt) {
        jdbcTemplate.update("""
                INSERT INTO t_service_invoke_log
                (id, trace_id, service_code, consumer_code, status_code, elapsed_millis, response_size, log_day, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, "trace-" + id, "svc", "consumer", 200, 10L, 128L, "202601", Timestamp.from(createdAt));
    }

    private JdbcDataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:partition-maintainer-" + java.util.UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        return dataSource;
    }

    private static final class RecordingMaintainer extends JdbcPartitionMaintainer {
        private final StringBuilder sql = new StringBuilder();

        private RecordingMaintainer() {
            super(new JdbcTemplate(), new InMemoryAuditLogRepository());
        }

        @Override
        protected void execute(String statement) {
            sql.append(statement).append('\n');
        }

        @Override
        protected java.util.List<String> partitions(String table) {
            return java.util.List.of("p202601", "pmax");
        }
    }
}
