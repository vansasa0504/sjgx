package com.platform.pipeline.storage.etl;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

public record DataAsset(String assetCode, Map<String, String> fields, Set<String> tags, Instant createdAt) {
    public DataAsset {
        fields = Map.copyOf(fields);
        tags = Set.copyOf(tags);
    }
}
