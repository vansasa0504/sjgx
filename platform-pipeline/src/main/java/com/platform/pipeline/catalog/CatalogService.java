package com.platform.pipeline.catalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.platform.common.db.IdGenerator;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.springframework.jdbc.core.JdbcTemplate;

public class CatalogService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final AtomicLong ids = new AtomicLong(1);
    private final IdGenerator idGenerator;
    private final List<DataCatalogItem> items = new ArrayList<>();
    private final JdbcTemplate jdbcTemplate;

    public CatalogService() {
        this(null);
    }

    public CatalogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = jdbcTemplate != null ? new IdGenerator(jdbcTemplate) : null;
    }



    public DataCatalogItem add(String catalogCode, String name, String subject, long partnerId, String dataType,
                               String scenario, List<String> fields, String format, String updateFrequency,
                               String source, String complianceNote, String usageLimit) {
        DataCatalogItem item = new DataCatalogItem(jdbcTemplate != null ? idGenerator.nextId("t_data_catalog") : ids.getAndIncrement(), catalogCode, name, subject, partnerId,
                dataType, scenario, List.copyOf(fields), format, updateFrequency, source, complianceNote, usageLimit);
        if (jdbcTemplate != null) {
            jdbcTemplate.update(
                    "INSERT INTO t_data_catalog (id, catalog_code, name, subject, partner_id, data_type, scenario, "
                            + "field_definitions, format, update_frequency, source, compliance_note, usage_limit) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    item.id(), item.catalogCode(), item.name(), item.subject(), item.partnerId(), item.dataType(),
                    item.scenario(), toJson(item.fieldDefinitions()), item.format(), item.updateFrequency(),
                    item.source(), item.complianceNote(), item.usageLimit());
        }
        items.add(item);
        return item;
    }

    public List<DataCatalogItem> query(String subject, Long partnerId, String dataType, String scenario) {
        if (jdbcTemplate != null) {
            return queryFromDb(subject, partnerId, dataType, scenario);
        }
        Stream<DataCatalogItem> stream = items.stream();
        if (subject != null) stream = stream.filter(i -> subject.equals(i.subject()));
        if (partnerId != null) stream = stream.filter(i -> partnerId == i.partnerId());
        if (dataType != null) stream = stream.filter(i -> dataType.equals(i.dataType()));
        if (scenario != null) stream = stream.filter(i -> scenario.equals(i.scenario()));
        return stream.toList();
    }

    private List<DataCatalogItem> queryFromDb(String subject, Long partnerId, String dataType, String scenario) {
        StringBuilder sql = new StringBuilder("SELECT id, catalog_code, name, subject, partner_id, data_type, "
                + "scenario, field_definitions, format, update_frequency, source, compliance_note, usage_limit "
                + "FROM t_data_catalog WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (subject != null) {
            sql.append(" AND subject = ?");
            params.add(subject);
        }
        if (partnerId != null) {
            sql.append(" AND partner_id = ?");
            params.add(partnerId);
        }
        if (dataType != null) {
            sql.append(" AND data_type = ?");
            params.add(dataType);
        }
        if (scenario != null) {
            sql.append(" AND scenario = ?");
            params.add(scenario);
        }
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new DataCatalogItem(
                rs.getLong("id"),
                rs.getString("catalog_code"),
                rs.getString("name"),
                rs.getString("subject"),
                rs.getLong("partner_id"),
                rs.getString("data_type"),
                rs.getString("scenario"),
                fromJson(rs.getString("field_definitions")),
                rs.getString("format"),
                rs.getString("update_frequency"),
                rs.getString("source"),
                rs.getString("compliance_note"),
                rs.getString("usage_limit")
        ), params.toArray());
    }

    public List<DataCatalogItem> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return jdbcTemplate != null ? queryFromDb(null, null, null, null) : List.copyOf(items);
        }
        if (jdbcTemplate != null) {
            String like = "%" + keyword + "%";
            return jdbcTemplate.query(
                    "SELECT id, catalog_code, name, subject, partner_id, data_type, scenario, field_definitions, "
                            + "format, update_frequency, source, compliance_note, usage_limit "
                            + "FROM t_data_catalog WHERE name LIKE ? OR catalog_code LIKE ? OR subject LIKE ?",
                    (rs, rowNum) -> new DataCatalogItem(
                            rs.getLong("id"),
                            rs.getString("catalog_code"),
                            rs.getString("name"),
                            rs.getString("subject"),
                            rs.getLong("partner_id"),
                            rs.getString("data_type"),
                            rs.getString("scenario"),
                            fromJson(rs.getString("field_definitions")),
                            rs.getString("format"),
                            rs.getString("update_frequency"),
                            rs.getString("source"),
                            rs.getString("compliance_note"),
                            rs.getString("usage_limit")
                    ), like, like, like);
        }
        return items.stream()
                .filter(i -> (i.name() != null && i.name().contains(keyword))
                        || (i.catalogCode() != null && i.catalogCode().contains(keyword))
                        || (i.subject() != null && i.subject().contains(keyword)))
                .toList();
    }

    public DataCatalogItem findById(long id) {
        if (jdbcTemplate != null) {
            try {
                return jdbcTemplate.queryForObject(
                        "SELECT id, catalog_code, name, subject, partner_id, data_type, scenario, field_definitions, "
                                + "format, update_frequency, source, compliance_note, usage_limit "
                                + "FROM t_data_catalog WHERE id = ?",
                        (rs, rowNum) -> new DataCatalogItem(
                                rs.getLong("id"),
                                rs.getString("catalog_code"),
                                rs.getString("name"),
                                rs.getString("subject"),
                                rs.getLong("partner_id"),
                                rs.getString("data_type"),
                                rs.getString("scenario"),
                                fromJson(rs.getString("field_definitions")),
                                rs.getString("format"),
                                rs.getString("update_frequency"),
                                rs.getString("source"),
                                rs.getString("compliance_note"),
                                rs.getString("usage_limit")
                        ), id);
            } catch (Exception ex) {
                return null;
            }
        }
        return items.stream().filter(i -> i.id() == id).findFirst().orElse(null);
    }

    public CatalogController.PreviewResult preview(DataCatalogItem item) {
        Map<String, String> row = new LinkedHashMap<>();
        for (String field : item.fieldDefinitions()) {
            row.put(field, sampleValue(field));
        }
        List<Map<String, String>> sample = row.isEmpty() ? List.of() : List.of(row);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("fieldCount", item.fieldDefinitions().size());
        stats.put("sampleCount", sample.size());
        stats.put("format", item.format());
        stats.put("updateFrequency", item.updateFrequency());
        return new CatalogController.PreviewResult(sample, stats,
                "字段可读，样例已按目录安全策略脱敏");
    }

    private String sampleValue(String field) {
        if (isSensitiveField(field)) {
            return "***MASKED***";
        }
        String normalized = field == null ? "value" : field.toLowerCase();
        if (normalized.contains("score")) {
            return "720";
        }
        if (normalized.contains("status")) {
            return "ACTIVE";
        }
        if (normalized.contains("date") || normalized.contains("time")) {
            return "2026-06-28T00:00:00Z";
        }
        return "sample_" + (field == null || field.isBlank() ? "value" : field);
    }

    private boolean isSensitiveField(String field) {
        if (field == null) {
            return false;
        }
        String normalized = field.toLowerCase();
        return normalized.contains("credential")
                || normalized.contains("secret")
                || normalized.contains("password")
                || normalized.contains("token")
                || normalized.contains("api_key")
                || normalized.contains("apikey")
                || normalized.contains("idcard")
                || normalized.contains("identity")
                || normalized.contains("person_id")
                || normalized.contains("phone")
                || normalized.contains("mobile");
    }

    private static String toJson(List<String> fields) {
        try {
            return MAPPER.writeValueAsString(fields);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private static List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }
}
