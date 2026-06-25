package com.platform.billing.stats;

import java.math.BigDecimal;
import java.time.Instant;

public record StatsSnapshot(
        Long id,
        MetricName metricName,
        StatsDimension dimension,
        Long dimensionId,
        BigDecimal metricValue,
        Instant snapshotAt
) {
}