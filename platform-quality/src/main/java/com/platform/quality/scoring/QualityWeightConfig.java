package com.platform.quality.scoring;

import com.platform.quality.rule.QualityDimension;

public record QualityWeightConfig(QualityDimension dimension, int weight, boolean enabled) {
    public QualityWeightConfig {
        weight = Math.max(0, weight);
    }
}
