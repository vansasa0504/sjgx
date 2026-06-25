package com.platform.pipeline.ingest;

import com.platform.common.exception.BusinessException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JsonConverter implements FormatConverter {
    @Override
    public String format() {
        return "JSON";
    }

    @Override
    public List<Map<String, String>> convert(String rawPayload) {
        String trimmed = rawPayload == null ? "" : rawPayload.trim();
        if (!(trimmed.startsWith("{") && trimmed.endsWith("}")) && !(trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            throw new BusinessException("INGEST-400", "invalid JSON payload");
        }
        if (trimmed.startsWith("[")) {
            return parseArray(trimmed);
        }
        return List.of(parseObject(trimmed));
    }

    private List<Map<String, String>> parseArray(String value) {
        String body = value.substring(1, value.length() - 1).trim();
        if (body.isEmpty()) {
            return List.of();
        }
        List<Map<String, String>> rows = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (ch == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    rows.add(parseObject(body.substring(start, i + 1)));
                }
            }
        }
        return rows;
    }

    private Map<String, String> parseObject(String value) {
        String body = value.substring(1, value.length() - 1).trim();
        Map<String, String> row = new LinkedHashMap<>();
        if (body.isEmpty()) {
            return row;
        }
        for (String pair : body.split(",")) {
            String[] parts = pair.split(":", 2);
            if (parts.length == 2) {
                row.put(unquote(parts[0]), unquote(parts[1]));
            }
        }
        return row;
    }

    private String unquote(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }
}
