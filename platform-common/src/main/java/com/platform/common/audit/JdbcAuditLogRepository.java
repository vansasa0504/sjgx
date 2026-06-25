package com.platform.common.audit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

public class JdbcAuditLogRepository implements AuditLogRepository {
    private final JdbcTemplate jdbcTemplate;
    private final AtomicLong ids = new AtomicLong(System.currentTimeMillis());

    public JdbcAuditLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public AuditEvent append(AuditEvent event) {
        long id = event.id() == null ? ids.incrementAndGet() : event.id();
        jdbcTemplate.update("""
                INSERT INTO t_audit_log
                (id, trace_id, event_type, actor_type, actor_id, target_type, target_id, action, detail, source_ip, user_agent, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, event.traceId(), event.eventType(), event.actorType(), event.actorId(), event.targetType(),
                event.targetId(), event.action(), event.detail(), event.sourceIp(), event.userAgent(),
                event.status().name(), Timestamp.from(event.createdAt()));
        return new AuditEvent(id, event.traceId(), event.eventType(), event.actorType(), event.actorId(),
                event.targetType(), event.targetId(), event.action(), event.detail(), event.sourceIp(),
                event.userAgent(), event.status(), event.createdAt());
    }

    @Override
    public List<AuditEvent> findByTraceId(String traceId) {
        return jdbcTemplate.query("SELECT * FROM t_audit_log WHERE trace_id = ?", mapper(), traceId);
    }

    @Override
    public List<AuditEvent> findByActor(String actorType, String actorId) {
        return jdbcTemplate.query("SELECT * FROM t_audit_log WHERE actor_type = ? AND actor_id = ?", mapper(), actorType, actorId);
    }

    @Override
    public List<AuditEvent> findByEventType(String eventType, Instant from, Instant to) {
        return jdbcTemplate.query("""
                SELECT * FROM t_audit_log
                WHERE event_type = ? AND created_at >= ? AND created_at <= ?
                """, mapper(), eventType, Timestamp.from(from), Timestamp.from(to));
    }

    private RowMapper<AuditEvent> mapper() {
        return new RowMapper<>() {
            @Override
            public AuditEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
                return new AuditEvent(rs.getLong("id"), rs.getString("trace_id"), rs.getString("event_type"),
                        rs.getString("actor_type"), rs.getString("actor_id"), rs.getString("target_type"),
                        rs.getString("target_id"), rs.getString("action"), rs.getString("detail"),
                        rs.getString("source_ip"), rs.getString("user_agent"),
                        AuditStatus.valueOf(rs.getString("status")), rs.getTimestamp("created_at").toInstant());
            }
        };
    }
}