package com.platform.pipeline.catalog;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryCatalogLineageRepository implements CatalogLineageRepository {
    private final Map<String, CatalogLineage> lineages = new ConcurrentHashMap<>();

    @Override
    public void save(CatalogLineage lineage) {
        lineages.put(key(lineage), lineage);
    }

    @Override
    public List<CatalogLineage> findByCatalogId(long catalogId) {
        return lineages.values().stream()
                .filter(lineage -> lineage.catalogId() == catalogId)
                .sorted((left, right) -> {
                    int direction = left.direction().compareTo(right.direction());
                    if (direction != 0) {
                        return direction;
                    }
                    return left.nodeType().compareTo(right.nodeType());
                })
                .toList();
    }

    private String key(CatalogLineage lineage) {
        return lineage.catalogId() + ":" + lineage.nodeType() + ":" + lineage.nodeId() + ":" + lineage.direction();
    }
}
