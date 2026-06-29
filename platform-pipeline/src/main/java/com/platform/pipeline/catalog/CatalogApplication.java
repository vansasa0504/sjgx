package com.platform.pipeline.catalog;

import java.time.Instant;

public record CatalogApplication(
        long id,
        long catalogId,
        String applicant,
        String reason,
        String scope,
        String status,
        String approver,
        Instant createdAt,
        Instant approvedAt
) {
    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
}
