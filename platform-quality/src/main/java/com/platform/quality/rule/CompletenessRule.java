package com.platform.quality.rule;

import java.util.List;
import java.util.Map;

public class CompletenessRule extends AbstractQualityRule {
    @Override
    public QualityDimension dimension() {
        return QualityDimension.COMPLETENESS;
    }

    @Override
    public List<QualityViolation> evaluate(List<Map<String, Object>> rows, QualityRuleConfig config) {
        return oneByOne(rows, config, row -> blank(value(row, config.field())) ? "required field is blank" : null);
    }
}
