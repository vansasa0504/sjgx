package com.platform.pipeline.catalog;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryCatalogQualitySummaryRepository implements CatalogQualitySummaryRepository {
    private final ConcurrentHashMap<Long, CatalogQualitySummary> summaries = new ConcurrentHashMap<>();

    @Override
    public CatalogQualitySummary upsert(long catalogId, double score, int issueCount) {
        CatalogQualitySummary summary = new CatalogQualitySummary(catalogId, score, issueCount, Instant.now());
        summaries.put(catalogId, summary);
        return summary;
    }

    @Override
    public Optional<CatalogQualitySummary> findByCatalogId(long catalogId) {
        return Optional.ofNullable(summaries.get(catalogId));
    }
}
