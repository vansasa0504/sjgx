package com.platform.quality.rule;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

abstract class AbstractQualityRule implements QualityRule {
    protected QualityViolation violation(QualityRuleConfig config, int rowIndex, String message) {
        return new QualityViolation(config.ruleCode(), dimension(), rowIndex, config.field(), message);
    }

    protected Object value(Map<String, Object> row, String field) {
        return row.get(field);
    }

    protected boolean blank(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }

    protected double number(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    protected Instant instant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        return Instant.parse(String.valueOf(value));
    }

    protected long longExpression(QualityRuleConfig config, String key, long fallback) {
        Object value = config.expressionValue(key);
        return value == null ? fallback : Long.parseLong(String.valueOf(value));
    }

    protected double doubleExpression(QualityRuleConfig config, String key, double fallback) {
        Object value = config.expressionValue(key);
        return value == null ? fallback : Double.parseDouble(String.valueOf(value));
    }

    protected List<QualityViolation> oneByOne(List<Map<String, Object>> rows, QualityRuleConfig config, RowCheck check) {
        List<QualityViolation> violations = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            String message = check.message(rows.get(i));
            if (message != null) {
                violations.add(violation(config, i, message));
            }
        }
        return violations;
    }

    protected interface RowCheck {
        String message(Map<String, Object> row);
    }
}
