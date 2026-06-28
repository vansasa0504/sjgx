package com.platform.quality.rule;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.platform.common.db.IdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JDBC 实现的 QualityRuleRepository，落 t_quality_rule 表。
 * 当 JdbcTemplate 不可用时，调用方应使用 InMemoryQualityRuleRepository。
 */
public class JdbcQualityRuleRepository implements QualityRuleRepository {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;

    public JdbcQualityRuleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = new IdGenerator(jdbcTemplate);
    }

    @Override
    public QualityRuleConfig save(QualityRuleConfig config) {
        Long id = config.id();
        if (id == null) {
            id = idGenerator.nextId("t_quality_rule");
        }
        String expressionJson = writeExpression(config.expression());
        int weight = config.weight();
        String severity = weight >= 80 ? "HIGH" : weight >= 40 ? "MEDIUM" : "LOW";
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_quality_rule WHERE id = ?", Integer.class, id);
        if (existing != null && existing > 0) {
            jdbcTemplate.update("""
                    UPDATE t_quality_rule SET rule_code=?, rule_name=?, dimension=?, target_object=?,
                    rule_expression=?, severity=?, enabled=? WHERE id=?
                    """, config.ruleCode(), config.ruleCode(), config.dimension().name(),
                    config.field(), expressionJson, severity, 1, id);
        } else {
            jdbcTemplate.update("""
                    INSERT INTO t_quality_rule (id, rule_code, rule_name, dimension, rule_type,
                    target_object, rule_expression, severity, enabled)
                    VALUES (?, ?, ?, ?, 'FIELD', ?, ?, ?, 1)
                    """, id, config.ruleCode(), config.ruleCode(), config.dimension().name(),
                    config.field(), expressionJson, severity);
        }
        return new QualityRuleConfig(id, config.ruleCode(), config.dimension(),
                config.field(), config.expression(), config.weight());
    }

    @Override
    public Optional<QualityRuleConfig> findById(long id) {
        List<QualityRuleConfig> results = jdbcTemplate.query(
                "SELECT id, rule_code, dimension, target_object, rule_expression FROM t_quality_rule WHERE id = ?",
                (rs, rowNum) -> mapRow(rs.getLong("id"), rs.getString("rule_code"),
                        rs.getString("dimension"), rs.getString("target_object"),
                        rs.getString("rule_expression")),
                id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<QualityRuleConfig> findAll(QualityDimension dimension, Boolean enabled) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, rule_code, dimension, target_object, rule_expression FROM t_quality_rule WHERE 1=1");
        List<Object> params = new java.util.ArrayList<>();
        if (dimension != null) {
            sql.append(" AND dimension = ?");
            params.add(dimension.name());
        }
        if (enabled != null) {
            sql.append(" AND enabled = ?");
            params.add(enabled ? 1 : 0);
        }
        sql.append(" ORDER BY id");
        return jdbcTemplate.query(sql.toString(),
                (rs, rowNum) -> mapRow(rs.getLong("id"), rs.getString("rule_code"),
                        rs.getString("dimension"), rs.getString("target_object"),
                        rs.getString("rule_expression")),
                params.toArray());
    }

    @Override
    public void delete(long id) {
        int affected = jdbcTemplate.update("DELETE FROM t_quality_rule WHERE id = ?", id);
        if (affected == 0) {
            throw new com.platform.common.exception.BusinessException("QUALITY-404", "quality rule not found");
        }
    }

    private QualityRuleConfig mapRow(long id, String ruleCode, String dimensionName,
                                     String targetObject, String expressionJson) {
        QualityDimension dimension = QualityDimension.valueOf(dimensionName);
        Map<String, Object> expression = readExpression(expressionJson);
        int weight = expression.containsKey("weight") && expression.get("weight") instanceof Number n
                ? n.intValue() : 100;
        return new QualityRuleConfig(id, ruleCode, dimension, targetObject, expression, weight);
    }

    private String writeExpression(Map<String, Object> expression) {
        try {
            return MAPPER.writeValueAsString(expression);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Map<String, Object> readExpression(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}