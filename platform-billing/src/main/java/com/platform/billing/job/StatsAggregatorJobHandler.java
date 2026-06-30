package com.platform.billing.job;

import com.platform.billing.stats.StatsAggregator;
import com.platform.billing.stats.StatsSnapshot;
import com.platform.common.model.ServiceInvokeLog;
import com.xxl.job.core.handler.annotation.XxlJob;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.BiFunction;

public class StatsAggregatorJobHandler {
    private final StatsAggregator statsAggregator;
    private final BiFunction<Instant, Instant, List<ServiceInvokeLog>> logSupplier;

    public StatsAggregatorJobHandler(StatsAggregator statsAggregator, BiFunction<Instant, Instant, List<ServiceInvokeLog>> logSupplier) {
        this.statsAggregator = statsAggregator;
        this.logSupplier = logSupplier;
    }

    public List<StatsSnapshot> execute(Instant snapshotAt) {
        return statsAggregator.aggregate(logSupplier.apply(snapshotAt.minus(Duration.ofDays(30)), snapshotAt), snapshotAt);
    }

    @XxlJob("statsAggregate")
    public void statsAggregate() {
        execute(Instant.now());
    }
}
