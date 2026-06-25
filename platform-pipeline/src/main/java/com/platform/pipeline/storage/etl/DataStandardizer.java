package com.platform.pipeline.storage.etl;

import java.util.LinkedHashMap;
import java.util.Map;

public class DataStandardizer {
    public Map<String, String> standardize(Map<String, String> row, Map<String, String> fieldMapping) {
        Map<String, String> standardized = new LinkedHashMap<>();
        row.forEach((field, value) -> standardized.put(fieldMapping.getOrDefault(field, field), value));
        return standardized;
    }
}
