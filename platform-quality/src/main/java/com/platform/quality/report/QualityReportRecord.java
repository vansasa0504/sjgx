package com.platform.quality.report;

import java.time.Instant;

public record QualityReportRecord(
        long id,
        String dimension,
        String dimensionValue,
        int checkCount,
        int passCount,
        int failCount,
        double failRate,
        double score,
        Instant generatedAt) {
}
