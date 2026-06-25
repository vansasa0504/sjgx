package com.platform.common.audit;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AuditEvent(
        Long id,
        String traceId,
        String eventType,
        String actorType,
        String actorId,
        String targetType,
        String targetId,
        String action,
        String detail,
        String sourceIp,
        String userAgent,
        AuditStatus status,
        Instant createdAt
) {
    public AuditEvent {
        traceId = Objects.requireNonNullElseGet(traceId, () -> UUID.randomUUID().toString());
        eventType = required(eventType, "eventType");
        actorType = Objects.requireNonNullElse(actorType, "SYSTEM");
        actorId = Objects.requireNonNullElse(actorId, "system");
        targetType = Objects.requireNonNullElse(targetType, "UNKNOWN");
        targetId = Objects.requireNonNullElse(targetId, "-");
        action = required(action, "action");
        detail = Objects.requireNonNullElse(detail, "");
        sourceIp = Objects.requireNonNullElse(sourceIp, "");
        userAgent = Objects.requireNonNullElse(userAgent, "");
        status = Objects.requireNonNullElse(status, AuditStatus.SUCCESS);
        createdAt = Objects.requireNonNullElseGet(createdAt, Instant::now);
    }

    public static AuditEvent system(String eventType, String action, String detail, AuditStatus status) {
        return new AuditEvent(null, null, eventType, "SYSTEM", "system", "METHOD", action, action, detail, "", "", status, Instant.now());
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}