package com.platform.pipeline.catalog;

import java.util.List;

public interface CatalogLineageRepository {
    void save(CatalogLineage lineage);

    List<CatalogLineage> findByCatalogId(long catalogId);
}
