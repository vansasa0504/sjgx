package com.platform.quality.executor;

import com.platform.quality.rule.AccuracyRule;
import com.platform.quality.rule.CompletenessRule;
import com.platform.quality.rule.ConsistencyRule;
import com.platform.quality.rule.QualityDimension;
import com.platform.quality.rule.QualityRule;
import com.platform.quality.rule.QualityRuleConfig;
import com.platform.quality.rule.QualityViolation;
import com.platform.quality.rule.TimelinessRule;
import com.platform.quality.rule.UniquenessRule;
import com.platform.quality.rule.ValidityRule;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class QualityCheckExecutor {
    private final Map<QualityDimension, QualityRule> rules;
    private final List<QualityCheckResult> history = new ArrayList<>();

    public QualityCheckExecutor() {
        this(List.of(new CompletenessRule(), new AccuracyRule(), new ConsistencyRule(),
                new TimelinessRule(), new ValidityRule(), new UniquenessRule()));
    }

    public QualityCheckExecutor(List<QualityRule> rules) {
        this.rules = new EnumMap<>(rules.stream().collect(Collectors.toMap(QualityRule::dimension, Function.identity())));
    }

    /**
     * Calculates row-level fail rate for E-04 ingest suspension semantics.
     * A row is counted as failed once when any configured rule reports at least one violation on that row.
     */
    public QualityCheckResult check(String batchNo,
                                    List<Map<String, Object>> rows,
                                    List<QualityRuleConfig> configs,
                                    double failRateThreshold) {
        List<QualityViolation> violations = new ArrayList<>();
        for (QualityRuleConfig config : configs) {
            QualityRule rule = rules.get(config.dimension());
            if (rule == null) {
                throw new IllegalArgumentException("unsupported quality dimension: " + config.dimension());
            }
            violations.addAll(rule.evaluate(rows, config));
        }
        Set<Integer> failedRows = violations.stream().map(QualityViolation::rowIndex).collect(Collectors.toCollection(HashSet::new));
        int failCount = failedRows.size();
        int totalCount = rows.size();
        int passCount = Math.max(0, totalCount - failCount);
        double failRate = totalCount == 0 ? 0 : failCount / (double) totalCount;
        QualityCheckResult result = new QualityCheckResult(null, batchNo, totalCount, passCount, failCount, failRate,
                failRate <= failRateThreshold, Instant.now(), violations);
        history.add(result);
        return result;
    }

    public List<QualityCheckResult> history() {
        return List.copyOf(history);
    }
}
