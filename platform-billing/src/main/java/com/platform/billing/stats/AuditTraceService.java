package com.platform.billing.stats;

import com.platform.common.audit.AuditEvent;
import com.platform.common.audit.AuditLogRepository;
import java.time.Instant;
import java.util.List;

public class AuditTraceService {
    private final AuditLogRepository repository;

    public AuditTraceService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public List<AuditEvent> byTrace(String traceId) {
        return repository.findByTraceId(traceId);
    }

    public List<AuditEvent> byActor(String actorType, String actorId) {
        return repository.findByActor(actorType, actorId);
    }

    public List<AuditEvent> byEventType(String eventType, Instant from, Instant to) {
        return repository.findByEventType(eventType, from, to);
    }
}