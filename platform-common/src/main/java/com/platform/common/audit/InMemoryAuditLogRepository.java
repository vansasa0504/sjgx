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
        String prevHash = events.isEmpty() ? "" : events.get(events.size() - 1).hash();
        String hash = AuditHashing.hash(prevHash, event);
        AuditEvent saved = new AuditEvent(ids.getAndIncrement(), event.traceId(), event.eventType(), event.actorType(),
                event.actorId(), event.targetType(), event.targetId(), event.action(), event.detail(),
                event.sourceIp(), event.userAgent(), event.status(), event.createdAt(), prevHash, hash);
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

    @Override
    public AuditChainVerification verify() {
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
}
