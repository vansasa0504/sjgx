package com.platform.pipeline.catalog;

import java.time.Instant;

public record CatalogQualitySummary(long catalogId, double score, int issueCount, Instant updatedAt) {
    public static CatalogQualitySummary empty(long catalogId) {
        return new CatalogQualitySummary(catalogId, 0.0, 0, Instant.EPOCH);
    }
}
