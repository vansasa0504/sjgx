package com.platform.common.audit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import com.platform.common.db.IdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

public class JdbcAuditLogRepository implements AuditLogRepository {
    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;

    public JdbcAuditLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = new IdGenerator(jdbcTemplate);
    }

    @Override
    public synchronized AuditEvent append(AuditEvent event) {
        long id = event.id() == null ? idGenerator.nextId("t_audit_log") : event.id();
        String prevHash = latestHash();
        String hash = AuditHashing.hash(prevHash, event);
        jdbcTemplate.update("""
                INSERT INTO t_audit_log
                (id, trace_id, event_type, actor_type, actor_id, target_type, target_id, action, detail, source_ip, user_agent, status, created_at, prev_hash, hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, event.traceId(), event.eventType(), event.actorType(), event.actorId(), event.targetType(),
                event.targetId(), event.action(), event.detail(), event.sourceIp(), event.userAgent(),
                event.status().name(), Timestamp.from(event.createdAt()), prevHash, hash);
        return new AuditEvent(id, event.traceId(), event.eventType(), event.actorType(), event.actorId(),
                event.targetType(), event.targetId(), event.action(), event.detail(), event.sourceIp(),
                event.userAgent(), event.status(), event.createdAt(), prevHash, hash);
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
        List<AuditEvent> events = jdbcTemplate.query("SELECT * FROM t_audit_log ORDER BY id", mapper());
        String previousHash = "";
        long checked = 0;
        for (AuditEvent event : events) {
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
        return AuditChainVerification.intact(checked);
    }

    private String latestHash() {
        List<String> hashes = jdbcTemplate.query("""
                SELECT hash FROM t_audit_log
                WHERE hash IS NOT NULL
                ORDER BY id DESC
                FETCH FIRST 1 ROWS ONLY
                """, (rs, rowNum) -> rs.getString("hash"));
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
