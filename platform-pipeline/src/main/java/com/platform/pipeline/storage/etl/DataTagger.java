package com.platform.pipeline.storage.etl;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class DataTagger {
    public Set<String> tag(Map<String, String> row) {
        Set<String> tags = new LinkedHashSet<>();
        if (row.containsKey("customerId") || row.containsKey("id")) {
            tags.add("CUSTOMER");
        }
        if (row.keySet().stream().anyMatch(field -> field.toLowerCase().contains("risk"))) {
            tags.add("RISK");
        }
        if (row.keySet().stream().anyMatch(field -> field.toLowerCase().contains("phone"))) {
            tags.add("PII");
        }
        return tags;
    }
}
