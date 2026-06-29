package com.platform.pipeline.catalog;

import java.time.Instant;

public record CatalogUsageSummary(long catalogId, long invokeCount, long applicationCount, Instant updatedAt) {
}
