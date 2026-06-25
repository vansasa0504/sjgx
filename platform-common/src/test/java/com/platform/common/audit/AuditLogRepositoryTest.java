package com.platform.common.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AuditLogRepositoryTest {
    @Test
    void jdbcRepositoryAppendsQueriesAndRejectsMutation() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
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
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        JdbcAuditLogRepository repository = new JdbcAuditLogRepository(jdbcTemplate);
        AuditEvent event = new AuditEvent(null, "trace-1", "BILL", "USER", "u1", "BILL", "b1",
                "generate", "detail", "127.0.0.1", "JUnit", AuditStatus.SUCCESS, Instant.now());

        AuditEvent saved = repository.append(event);

        assertEquals(1, repository.findByTraceId("trace-1").size());
        assertEquals(1, repository.findByActor("USER", "u1").size());
        assertEquals(1, repository.findByEventType("BILL", saved.createdAt().minusSeconds(1), saved.createdAt().plusSeconds(1)).size());
        assertThrows(UnsupportedOperationException.class, () -> repository.update(saved));
        assertThrows(UnsupportedOperationException.class, () -> repository.delete(saved.traceId()));
    }

    private JdbcDataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:audit;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        return dataSource;
    }
}