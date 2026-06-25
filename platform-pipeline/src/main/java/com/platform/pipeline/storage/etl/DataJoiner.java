package com.platform.pipeline.storage.etl;

import java.util.LinkedHashMap;
import java.util.Map;

public class DataJoiner {
    public Map<String, String> join(Map<String, String> left, Map<String, String> right) {
        Map<String, String> joined = new LinkedHashMap<>(left);
        joined.putAll(right);
        return joined;
    }
}
