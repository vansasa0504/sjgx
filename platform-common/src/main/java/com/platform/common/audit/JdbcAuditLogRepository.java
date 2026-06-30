package com.platform.common.audit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import com.platform.common.db.IdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

public class JdbcAuditLogRepository implements AuditLogRepository {
    private static final int VERIFY_BATCH_SIZE = 1000;

    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;

    public JdbcAuditLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = new IdGenerator(jdbcTemplate);
    }

    @Override
    public synchronized AuditEvent append(AuditEvent event) {
        long id = event.id() == null ? idGenerator.nextId("t_audit_log") : event.id();
        Instant createdAt = event.createdAt().truncatedTo(ChronoUnit.SECONDS);
        AuditEvent persistedEvent = new AuditEvent(id, event.traceId(), event.eventType(), event.actorType(),
                event.actorId(), event.targetType(), event.targetId(), event.action(), event.detail(),
                event.sourceIp(), event.userAgent(), event.status(), createdAt);
        String prevHash = latestHash();
        String hash = AuditHashing.hash(prevHash, persistedEvent);
        jdbcTemplate.update("""
                INSERT INTO t_audit_log
                (id, trace_id, event_type, actor_type, actor_id, target_type, target_id, action, detail, source_ip, user_agent, status, created_at, prev_hash, hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, persistedEvent.traceId(), persistedEvent.eventType(), persistedEvent.actorType(), persistedEvent.actorId(), persistedEvent.targetType(),
                persistedEvent.targetId(), persistedEvent.action(), persistedEvent.detail(), persistedEvent.sourceIp(), persistedEvent.userAgent(),
                persistedEvent.status().name(), Timestamp.from(createdAt), prevHash, hash);
        return new AuditEvent(id, persistedEvent.traceId(), persistedEvent.eventType(), persistedEvent.actorType(), persistedEvent.actorId(),
                persistedEvent.targetType(), persistedEvent.targetId(), persistedEvent.action(), persistedEvent.detail(), persistedEvent.sourceIp(),
                persistedEvent.userAgent(), persistedEvent.status(), createdAt, prevHash, hash);
    }

    @Override
    public List<AuditEvent> findByTraceId(String traceId) {
        return jdbcTemplate.query("SELECT * FROM t_audit_log WHERE trace_id = ? ORDER BY id", mapper(), traceId);
    }

    @Override
    public List<AuditEvent> findByActor(String actorType, String actorId) {
        return jdbcTemplate.query("SELECT * FROM t_audit_log WHERE actor_type = ? AND actor_id = ? ORDER BY id", mapper(), actorType, actorId);
    }

    @Override
    public List<AuditEvent> findByEventType(String eventType, Instant from, Instant to) {
        return jdbcTemplate.query("""
                SELECT * FROM t_audit_log
                WHERE event_type = ? AND created_at >= ? AND created_at <= ?
                ORDER BY id
                """, mapper(), eventType, Timestamp.from(from), Timestamp.from(to));
    }

    @Override
    public AuditChainVerification verify() {
        String previousHash = "";
        long checked = 0;
        long lastId = 0L;
        while (true) {
            List<AuditEvent> events = jdbcTemplate.query("""
                    SELECT * FROM t_audit_log
                    WHERE id > ?
                    ORDER BY id
                    LIMIT ?
                    """, mapper(), lastId, VERIFY_BATCH_SIZE);
            if (events.isEmpty()) {
                return AuditChainVerification.intact(checked);
            }
            for (AuditEvent event : events) {
                lastId = event.id() == null ? lastId : event.id();
                if (event.hash() == null || event.hash().isBlank()) {
                    previousHash = "";
                    continue;
                }
                if (!previousHash.equals(event.prevHash())) {
                    return AuditChainVerification.broken(checked + 1, event.id(), "prev_mismatch");
                }
                String expected = AuditHashing.hash(event.prevHash(), event);
                if (!expected.equals(event.hash())) {
                    return AuditChainVerification.broken(checked + 1, event.id(), "hash_mismatch");
                }
                previousHash = event.hash();
                checked++;
            }
        }
    }

    private String latestHash() {
        List<String> hashes = jdbcTemplate.query(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    SELECT hash FROM t_audit_log
                    WHERE hash IS NOT NULL
                    ORDER BY id DESC
                    """);
            statement.setMaxRows(1);
            return statement;
        }, (rs, rowNum) -> rs.getString("hash"));
        return hashes.isEmpty() ? "" : hashes.get(0);
    }

    private RowMapper<AuditEvent> mapper() {
        return new RowMapper<>() {
            @Override
            public AuditEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
                return new AuditEvent(rs.getLong("id"), rs.getString("trace_id"), rs.getString("event_type"),
                        rs.getString("actor_type"), rs.getString("actor_id"), rs.getString("target_type"),
                        rs.getString("target_id"), rs.getString("action"), rs.getString("detail"),
                        rs.getString("source_ip"), rs.getString("user_agent"),
                        AuditStatus.valueOf(rs.getString("status")), rs.getTimestamp("created_at").toInstant(),
                        rs.getString("prev_hash"), rs.getString("hash"));
            }
        };
    }
}
