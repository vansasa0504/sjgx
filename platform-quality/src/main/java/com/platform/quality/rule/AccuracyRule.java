package com.platform.quality.rule;

import java.util.List;
import java.util.Map;

public class AccuracyRule extends AbstractQualityRule {
    @Override
    public QualityDimension dimension() {
        return QualityDimension.ACCURACY;
    }

    @Override
    public List<QualityViolation> evaluate(List<Map<String, Object>> rows, QualityRuleConfig config) {
        double min = doubleExpression(config, "min", Double.NEGATIVE_INFINITY);
        double max = doubleExpression(config, "max", Double.POSITIVE_INFINITY);
        return oneByOne(rows, config, row -> {
            try {
                double actual = number(value(row, config.field()));
                return actual < min || actual > max ? "value outside allowed range" : null;
            } catch (Exception ex) {
                return "value is not numeric";
            }
        });
    }
}
