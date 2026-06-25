package com.platform.pipeline.ingest;

import com.platform.common.exception.BusinessException;
import com.platform.quality.executor.QualityCheckExecutor;
import com.platform.quality.executor.QualityCheckResult;
import com.platform.quality.rule.QualityRuleConfig;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class IngestQualityGuard {
    private final QualityCheckExecutor executor;
    private final List<QualityRuleConfig> rules;
    private final double failRateThreshold;

    public IngestQualityGuard(QualityCheckExecutor executor, List<QualityRuleConfig> rules, double failRateThreshold) {
        this.executor = executor;
        this.rules = List.copyOf(rules);
        this.failRateThreshold = failRateThreshold;
    }

    public static IngestQualityGuard disabled() {
        return new IngestQualityGuard(new QualityCheckExecutor(), List.of(), 1.0);
    }

    public QualityCheckResult validate(List<Map<String, String>> rows) {
        if (rules.isEmpty()) {
            return new QualityCheckResult("INGEST", rows.size(), 0, 0, true, Instant.now(), List.of());
        }
        List<Map<String, Object>> objectRows = rows.stream().map(this::objectRow).toList();
        QualityCheckResult result = executor.check("INGEST", objectRows, rules, failRateThreshold);
        if (!result.passed()) {
            throw new BusinessException("INGEST-QUALITY", "quality fail rate exceeded threshold");
        }
        return result;
    }

    private Map<String, Object> objectRow(Map<String, String> row) {
        Map<String, Object> converted = new LinkedHashMap<>();
        row.forEach(converted::put);
        return converted;
    }
}
