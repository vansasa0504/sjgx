package com.platform.common.audit;

import java.time.Instant;
import java.util.List;

public interface AuditLogRepository {
    AuditEvent append(AuditEvent event);

    List<AuditEvent> findByTraceId(String traceId);

    List<AuditEvent> findByActor(String actorType, String actorId);

    List<AuditEvent> findByEventType(String eventType, Instant from, Instant to);

    default void update(AuditEvent event) {
        throw new UnsupportedOperationException("audit log is append-only");
    }

    default void delete(String traceId) {
        throw new UnsupportedOperationException("audit log is append-only");
    }
}