package com.platform.pipeline.storage.lifecycle;

import java.time.Instant;
import java.util.Objects;

public record LifecycleEvent(
        String assetCode,
        LifecycleAction action,
        Instant operatedAt,
        String operator,
        String reason,
        String proofHash,
        String objectKey
) {
    public LifecycleEvent(String assetCode, LifecycleAction action, Instant operatedAt) {
        this(assetCode, action, operatedAt, "system", "policy-scan", null, assetCode);
    }

    public LifecycleEvent {
        assetCode = required(assetCode, "assetCode");
        action = Objects.requireNonNull(action, "action");
        operatedAt = Objects.requireNonNull(operatedAt, "operatedAt");
        operator = Objects.requireNonNullElse(operator, "system");
        reason = Objects.requireNonNullElse(reason, "");
        objectKey = Objects.requireNonNullElse(objectKey, assetCode);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
