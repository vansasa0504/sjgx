package com.platform.pipeline.ingest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.exception.BusinessException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JsonConverter implements FormatConverter {
    private final ObjectMapper objectMapper;

    public JsonConverter() {
        this(new ObjectMapper());
    }

    JsonConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String format() {
        return "JSON";
    }

    @Override
    public List<Map<String, String>> convert(String rawPayload) {
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            if (root.isArray()) {
                return objectMapper.convertValue(root, new TypeReference<List<Map<String, Object>>>() {})
                        .stream().map(this::stringify).toList();
            }
            if (root.isObject()) {
                Map<String, Object> row = objectMapper.convertValue(root, new TypeReference<Map<String, Object>>() {});
                return List.of(stringify(row));
            }
            throw new BusinessException("INGEST-400", "JSON root must be object or array");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("INGEST-400", "invalid JSON payload");
        }
    }

    private Map<String, String> stringify(Map<String, Object> row) {
        Map<String, String> converted = new LinkedHashMap<>();
        row.forEach((key, value) -> converted.put(key, value == null ? null : String.valueOf(value)));
        return converted;
    }
}
