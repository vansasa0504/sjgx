package com.platform.pipeline.catalog;

import java.util.Optional;

public interface CatalogQualitySummaryRepository {
    CatalogQualitySummary upsert(long catalogId, double score, int issueCount);

    Optional<CatalogQualitySummary> findByCatalogId(long catalogId);
}
