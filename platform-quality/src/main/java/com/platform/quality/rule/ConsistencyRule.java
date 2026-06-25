package com.platform.quality.rule;

import java.util.List;
import java.util.Map;

public class ConsistencyRule extends AbstractQualityRule {
    @Override
    public QualityDimension dimension() {
        return QualityDimension.CONSISTENCY;
    }

    @Override
    public List<QualityViolation> evaluate(List<Map<String, Object>> rows, QualityRuleConfig config) {
        String compareField = String.valueOf(config.expressionValue("equalsField"));
        return oneByOne(rows, config, row -> {
            Object left = value(row, config.field());
            Object right = value(row, compareField);
            return left != null && left.equals(right) ? null : "field is inconsistent with " + compareField;
        });
    }
}
