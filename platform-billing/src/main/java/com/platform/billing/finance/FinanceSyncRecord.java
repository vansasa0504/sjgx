package com.platform.billing.finance;

import java.time.Instant;

public record FinanceSyncRecord(
        long id,
        String billNo,
        String adapterType,
        String externalNo,
        String status,
        int retryCount,
        String message,
        Instant syncedAt) {
}
