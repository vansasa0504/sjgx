package com.platform.common.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.sql.Connection;
import java.sql.SQLException;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.api.Trigger;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AuditLogRepositoryTest {
    @Test
    void jdbcRepositoryAppendsHashChainAndQueriesInOrder() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource("audit_chain"));
        createAuditTable(jdbcTemplate);
        JdbcAuditLogRepository repository = new JdbcAuditLogRepository(jdbcTemplate);
        AuditEvent event = new AuditEvent(null, "trace-1", "BILL", "USER", "u1", "BILL", "b1",
                "generate", "detail", "127.0.0.1", "JUnit", AuditStatus.SUCCESS, Instant.now());

        AuditEvent saved = repository.append(event);
        AuditEvent second = repository.append(new AuditEvent(null, "trace-1", "BILL", "USER", "u1", "BILL", "b2",
                "confirm", "detail-2", "127.0.0.1", "JUnit", AuditStatus.SUCCESS, saved.createdAt().plusMillis(1)));

        assertEquals(1L, saved.id());
        assertEquals(2L, second.id());
        assertEquals("", saved.prevHash());
        assertNotNull(saved.hash());
        assertEquals(saved.hash(), second.prevHash());
        assertEquals(AuditHashing.hash(saved.prevHash(), saved), saved.hash());
        assertEquals(AuditHashing.hash(second.prevHash(), second), second.hash());
        assertEquals(2, repository.findByTraceId("trace-1").size());
        assertEquals(2, repository.findByActor("USER", "u1").size());
        assertEquals(2, repository.findByEventType("BILL", saved.createdAt().minusSeconds(1), second.createdAt().plusSeconds(1)).size());
        assertTrue(repository.verify().intact());
        assertThrows(UnsupportedOperationException.class, () -> repository.update(saved));
        assertThrows(UnsupportedOperationException.class, () -> repository.delete(saved.traceId()));
    }

    @Test
    void verifyDetectsTamperedDetail() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource("audit_tamper"));
        createAuditTable(jdbcTemplate);
        JdbcAuditLogRepository repository = new JdbcAuditLogRepository(jdbcTemplate);
        AuditEvent first = repository.append(new AuditEvent(null, "trace-2", "BILL", "USER", "u1", "BILL", "b1",
                "generate", "detail", "", "", AuditStatus.SUCCESS, Instant.now()));
        repository.append(new AuditEvent(null, "trace-2", "BILL", "USER", "u1", "BILL", "b2",
                "confirm", "detail-2", "", "", AuditStatus.SUCCESS, first.createdAt().plusMillis(1)));

        jdbcTemplate.update("UPDATE t_audit_log SET detail = ? WHERE id = ?", "tampered", first.id());

        AuditChainVerification verification = repository.verify();
        assertFalse(verification.intact());
        assertEquals(first.id(), verification.firstBrokenId());
        assertEquals("hash_mismatch", verification.reason());
    }

    @Test
    void verifySkipsHistoricalRowsWithoutHash() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource("audit_history"));
        createAuditTable(jdbcTemplate);
        jdbcTemplate.update("""
                INSERT INTO t_audit_log
                (id, trace_id, event_type, actor_type, actor_id, target_type, target_id, action, detail, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, 9L, "old-trace", "OLD", "USER", "u1", "OLD", "o1", "OLD", "legacy", "SUCCESS", java.sql.Timestamp.from(Instant.now()));
        JdbcAuditLogRepository repository = new JdbcAuditLogRepository(jdbcTemplate);
        AuditEvent saved = repository.append(new AuditEvent(null, "trace-new", "BILL", "USER", "u1", "BILL", "b1",
                "generate", "detail", "", "", AuditStatus.SUCCESS, Instant.now()));

        assertEquals(10L, saved.id());
        assertEquals("", saved.prevHash());
        AuditChainVerification verification = repository.verify();
        assertTrue(verification.intact());
        assertEquals(1L, verification.totalChecked());
    }

    @Test
    void h2TriggerRejectsAuditUpdateAndDelete() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource("audit_append_only"));
        createAuditTable(jdbcTemplate);
        JdbcAuditLogRepository repository = new JdbcAuditLogRepository(jdbcTemplate);
        AuditEvent saved = repository.append(new AuditEvent(null, "trace-3", "BILL", "USER", "u1", "BILL", "b1",
                "generate", "detail", "", "", AuditStatus.SUCCESS, Instant.now()));
        jdbcTemplate.execute("CREATE TRIGGER audit_no_update BEFORE UPDATE ON t_audit_log FOR EACH ROW CALL \"" + NoMutationTrigger.class.getName() + "\"");
        jdbcTemplate.execute("CREATE TRIGGER audit_no_delete BEFORE DELETE ON t_audit_log FOR EACH ROW CALL \"" + NoMutationTrigger.class.getName() + "\"");

        assertThrows(Exception.class, () -> jdbcTemplate.update("UPDATE t_audit_log SET detail = ? WHERE id = ?", "bad", saved.id()));
        assertThrows(Exception.class, () -> jdbcTemplate.update("DELETE FROM t_audit_log WHERE id = ?", saved.id()));
    }

    private void createAuditTable(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE t_audit_log (
                    id BIGINT PRIMARY KEY,
                    trace_id VARCHAR(64) NOT NULL,
                    event_type VARCHAR(64) NOT NULL,
                    actor_type VARCHAR(32) NOT NULL,
                    actor_id VARCHAR(64) NOT NULL,
                    target_type VARCHAR(64) NOT NULL,
                    target_id VARCHAR(64) NOT NULL,
                    action VARCHAR(128) NOT NULL,
                    detail CLOB,
                    source_ip VARCHAR(64),
                    user_agent VARCHAR(256),
                    status VARCHAR(32) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    prev_hash VARCHAR(64),
                    hash VARCHAR(64)
                )
                """);
    }

    private JdbcDataSource dataSource(String name) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        return dataSource;
    }

    public static class NoMutationTrigger implements Trigger {
        @Override
        public void init(Connection conn, String schemaName, String triggerName, String tableName, boolean before, int type) {
        }

        @Override
        public void fire(Connection conn, Object[] oldRow, Object[] newRow) throws SQLException {
            throw new SQLException("audit is append-only");
        }

        @Override
        public void close() {
        }

        @Override
        public void remove() {
        }
    }
}
