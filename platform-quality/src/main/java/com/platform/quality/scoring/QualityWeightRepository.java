package com.platform.quality.scoring;

import com.platform.quality.rule.QualityDimension;

import java.util.List;

public interface QualityWeightRepository {
    List<QualityWeightConfig> enabledWeights();

    static QualityWeightRepository empty() {
        return List::of;
    }
}
