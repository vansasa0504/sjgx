package com.platform.quality.scoring;

import com.platform.quality.executor.QualityCheckResult;
import com.platform.quality.rule.QualityDimension;
import com.platform.quality.rule.QualityRuleConfig;
import com.platform.quality.rule.QualityViolation;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class QualityScoringService {
    private final QualityWeightRepository weightRepository;

    public QualityScoringService() {
        this(QualityWeightRepository.empty());
    }

    public QualityScoringService(QualityWeightRepository weightRepository) {
        this.weightRepository = weightRepository;
    }

    public QualityScore score(QualityCheckResult result, List<QualityRuleConfig> configs) {
        Map<QualityDimension, Integer> weights = weights(configs);
        Map<QualityDimension, Long> ruleCounts = configs.stream()
                .collect(Collectors.groupingBy(QualityRuleConfig::dimension, () -> new EnumMap<>(QualityDimension.class), Collectors.counting()));
        int totalWeight = Math.max(1, weights.values().stream().mapToInt(Integer::intValue).sum());
        Map<QualityDimension, Long> violationByDimension = result.violations().stream()
                .collect(Collectors.groupingBy(QualityViolation::dimension, () -> new EnumMap<>(QualityDimension.class), Collectors.counting()));
        double penalty = 0;
        for (Map.Entry<QualityDimension, Integer> entry : weights.entrySet()) {
            long violations = violationByDimension.getOrDefault(entry.getKey(), 0L);
            long dimensionRuleCount = Math.max(1, ruleCounts.getOrDefault(entry.getKey(), 1L));
            double denominator = Math.max(1, result.totalRows() * dimensionRuleCount);
            double dimensionFailRate = Math.min(1.0, violations / denominator);
            penalty += dimensionFailRate * entry.getValue();
        }
        double score = Math.max(0, 100 - (penalty / totalWeight) * 100);
        return new QualityScore(score, grade(score));
    }

    private Map<QualityDimension, Integer> weights(List<QualityRuleConfig> configs) {
        Map<QualityDimension, Integer> configured = new EnumMap<>(QualityDimension.class);
        for (QualityWeightConfig config : weightRepository.enabledWeights()) {
            configured.merge(config.dimension(), config.weight(), Integer::sum);
        }
        if (!configured.isEmpty()) {
            return configured;
        }
        for (QualityRuleConfig config : configs) {
            configured.merge(config.dimension(), config.weight(), Integer::sum);
        }
        return configured;
    }

    private String grade(double score) {
        if (score >= 90) return "A";
        if (score >= 80) return "B";
        if (score >= 60) return "C";
        return "D";
    }
}
