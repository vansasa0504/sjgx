package com.platform.quality.scoring;

import java.util.List;

public class InMemoryQualityWeightRepository implements QualityWeightRepository {
    private final List<QualityWeightConfig> weights;

    public InMemoryQualityWeightRepository(List<QualityWeightConfig> weights) {
        this.weights = List.copyOf(weights);
    }

    @Override
    public List<QualityWeightConfig> enabledWeights() {
        return weights.stream().filter(QualityWeightConfig::enabled).toList();
    }
}
