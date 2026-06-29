package com.platform.pipeline.catalog;

import java.util.List;

public record CatalogDetail(
        DataCatalogItem meta,
        List<CatalogLineage> lineage,
        CatalogQualitySummary quality,
        CatalogUsageSummary usage) {
}
