package com.platform.pipeline.ingest.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.platform.common.exception.BusinessException;
import com.platform.pipeline.ingest.FormatConverter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class XmlConverter implements FormatConverter {
    private final XmlMapper xmlMapper = new XmlMapper();

    @Override
    public String format() { return "XML"; }

    @Override
    public List<Map<String, String>> convert(String rawPayload) {
        try {
            JsonNode root = xmlMapper.readTree(rawPayload);
            Map<String, String> row = new LinkedHashMap<>();
            root.fields().forEachRemaining(e -> row.put(e.getKey(), e.getValue().asText(e.getValue().toString())));
            return List.of(row);
        } catch (Exception ex) {
            throw new BusinessException("INGEST-400", "invalid XML payload");
        }
    }
}
