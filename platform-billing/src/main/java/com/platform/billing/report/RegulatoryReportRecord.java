package com.platform.billing.report;

import java.time.Instant;

public record RegulatoryReportRecord(
        long id,
        String reportType,
        Instant periodFrom,
        Instant periodTo,
        String content,
        String status,
        String receiptNo,
        String receiptMessage,
        Instant generatedAt,
        Instant submittedAt) {
}
