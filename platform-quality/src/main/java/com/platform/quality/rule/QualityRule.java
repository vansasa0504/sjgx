package com.platform.quality.rule;

import java.util.List;
import java.util.Map;

public interface QualityRule {
    QualityDimension dimension();

    List<QualityViolation> evaluate(List<Map<String, Object>> rows, QualityRuleConfig config);
}
