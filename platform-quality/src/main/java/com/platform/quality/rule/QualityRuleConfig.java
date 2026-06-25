package com.platform.quality.rule;

import java.util.Map;

public record QualityRuleConfig(String ruleCode,
                                QualityDimension dimension,
                                String field,
                                Map<String, Object> expression,
                                int weight) {
    public QualityRuleConfig {
        expression = expression == null ? Map.of() : Map.copyOf(expression);
        weight = Math.max(0, weight);
    }

    public Object expressionValue(String key) {
        return expression.get(key);
    }
}
