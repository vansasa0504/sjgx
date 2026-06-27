package com.platform.quality.rule;

import java.util.Map;

public record QualityRuleConfig(Long id,
                                String ruleCode,
                                QualityDimension dimension,
                                String field,
                                Map<String, Object> expression,
                                int weight) {
    public QualityRuleConfig(String ruleCode, QualityDimension dimension, String field,
                             Map<String, Object> expression, int weight) {
        this(null, ruleCode, dimension, field, expression, weight);
    }

    public QualityRuleConfig {
        expression = expression == null ? Map.of() : Map.copyOf(expression);
        weight = Math.max(0, weight);
    }

    public Object expressionValue(String key) {
        return expression.get(key);
    }
}
