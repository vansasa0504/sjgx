package com.platform.common.audit;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryAuditLogRepository implements AuditLogRepository {
    private final AtomicLong ids = new AtomicLong(1);
    private final List<AuditEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public AuditEvent append(AuditEvent event) {
        AuditEvent saved = new AuditEvent(ids.getAndIncrement(), event.traceId(), event.eventType(), event.actorType(),
                event.actorId(), event.targetType(), event.targetId(), event.action(), event.detail(),
                event.sourceIp(), event.userAgent(), event.status(), event.createdAt());
        events.add(saved);
        return saved;
    }

    @Override
    public List<AuditEvent> findByTraceId(String traceId) {
        return events.stream().filter(event -> event.traceId().equals(traceId)).toList();
    }

    @Override
    public List<AuditEvent> findByActor(String actorType, String actorId) {
        return events.stream()
                .filter(event -> event.actorType().equals(actorType) && event.actorId().equals(actorId))
                .toList();
    }

    @Override
    public List<AuditEvent> findByEventType(String eventType, Instant from, Instant to) {
        return events.stream()
                .filter(event -> event.eventType().equals(eventType))
                .filter(event -> !event.createdAt().isBefore(from) && !event.createdAt().isAfter(to))
                .toList();
    }
}