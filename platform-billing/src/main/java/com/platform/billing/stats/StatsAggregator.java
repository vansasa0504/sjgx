package com.platform.billing.stats;

import com.platform.common.model.ServiceInvokeLog;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

public class StatsAggregator {
    private final StatsSnapshotRepository repository;
    private final CacheMetricsProvider cacheMetricsProvider;

    public StatsAggregator(StatsSnapshotRepository repository, CacheMetricsProvider cacheMetricsProvider) {
        this.repository = repository;
        this.cacheMetricsProvider = cacheMetricsProvider;
    }

    public List<StatsSnapshot> aggregate(List<ServiceInvokeLog> logs, Instant snapshotAt) {
        BigDecimal invokeCount = BigDecimal.valueOf(logs.size());
        BigDecimal successCount = BigDecimal.valueOf(logs.stream().filter(log -> log.status() >= 200 && log.status() < 300).count());
        BigDecimal successRate = logs.isEmpty() ? BigDecimal.ZERO : successCount.divide(invokeCount, 4, RoundingMode.HALF_UP);
        BigDecimal transferBytes = BigDecimal.valueOf(logs.stream().mapToLong(ServiceInvokeLog::responseSize).sum());
        return List.of(
                repository.save(new StatsSnapshot(null, MetricName.INVOKE_COUNT, StatsDimension.SERVICE, null, invokeCount, snapshotAt)),
                repository.save(new StatsSnapshot(null, MetricName.SUCCESS_RATE, StatsDimension.SERVICE, null, successRate, snapshotAt)),
                repository.save(new StatsSnapshot(null, MetricName.TRANSFER_BYTES, StatsDimension.SERVICE, null, transferBytes, snapshotAt)),
                repository.save(new StatsSnapshot(null, MetricName.CACHE_HIT_RATE, StatsDimension.SERVICE, null, cacheMetricsProvider.hitRate(), snapshotAt))
        );
    }
}
