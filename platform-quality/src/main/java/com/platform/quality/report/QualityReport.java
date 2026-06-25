package com.platform.quality.report;

import com.platform.quality.executor.QualityCheckResult;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record QualityReport(String targetType,
                            int checkCount,
                            double averageFailRate,
                            Map<Boolean, Long> passDistribution) {
    public static QualityReport from(String targetType, List<QualityCheckResult> checks) {
        List<QualityCheckResult> matched = checks.stream()
                .filter(check -> targetType.equals(check.targetType()))
                .toList();
        double average = matched.stream().mapToDouble(QualityCheckResult::failRate).average().orElse(0);
        Map<Boolean, Long> distribution = matched.stream()
                .collect(Collectors.groupingBy(QualityCheckResult::passed, Collectors.counting()));
        return new QualityReport(targetType, matched.size(), average, distribution);
    }
}
