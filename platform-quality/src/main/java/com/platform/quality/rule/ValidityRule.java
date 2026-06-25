package com.platform.quality.rule;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class ValidityRule extends AbstractQualityRule {
    @Override
    public QualityDimension dimension() {
        return QualityDimension.VALIDITY;
    }

    @Override
    public List<QualityViolation> evaluate(List<Map<String, Object>> rows, QualityRuleConfig config) {
        Pattern pattern = Pattern.compile(String.valueOf(config.expressionValue("regex")));
        return oneByOne(rows, config, row -> {
            Object actual = value(row, config.field());
            return actual != null && pattern.matcher(String.valueOf(actual)).matches() ? null : "value does not match regex";
        });
    }
}
