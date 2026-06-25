package com.platform.quality.executor;

import com.platform.quality.rule.QualityViolation;

import java.time.Instant;
import java.util.List;

public record QualityCheckResult(Long ruleId,
                                 String batchNo,
                                 int totalRows,
                                 int passCount,
                                 int failCount,
                                 double failRate,
                                 boolean passed,
                                 Instant checkedAt,
                                 List<QualityViolation> violations) {
    public QualityCheckResult(String targetType,
                              int totalRows,
                              int violationCount,
                              double failRate,
                              boolean passed,
                              Instant checkedAt,
                              List<QualityViolation> violations) {
        this(null, targetType, totalRows, Math.max(0, totalRows - violationCount), violationCount, failRate, passed, checkedAt, violations);
    }

    public QualityCheckResult {
        batchNo = batchNo == null || batchNo.isBlank() ? "BATCH-UNKNOWN" : batchNo;
        violations = List.copyOf(violations);
    }

    public String targetType() {
        return batchNo;
    }

    public int violationCount() {
        return failCount;
    }
}
