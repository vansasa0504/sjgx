package com.platform.quality.rule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UniquenessRule extends AbstractQualityRule {
    @Override
    public QualityDimension dimension() {
        return QualityDimension.UNIQUENESS;
    }

    @Override
    public List<QualityViolation> evaluate(List<Map<String, Object>> rows, QualityRuleConfig config) {
        List<QualityViolation> violations = new ArrayList<>();
        Set<Object> seen = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            Object actual = value(rows.get(i), config.field());
            if (actual == null || !seen.add(actual)) {
                violations.add(violation(config, i, "value is duplicated or missing"));
            }
        }
        return violations;
    }
}
