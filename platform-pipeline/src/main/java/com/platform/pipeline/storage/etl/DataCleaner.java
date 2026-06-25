package com.platform.pipeline.storage.etl;

import java.util.Map;
import java.util.Set;

public class DataCleaner {
    public Map<String, String> clean(Map<String, String> row, Set<String> requiredFields) {
        for (String field : requiredFields) {
            String value = row.get(field);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("missing required field: " + field);
            }
        }
        return row.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().trim(), (a, b) -> b, java.util.LinkedHashMap::new));
    }
}
