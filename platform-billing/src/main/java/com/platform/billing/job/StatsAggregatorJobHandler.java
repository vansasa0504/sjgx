package com.platform.billing.job;

import com.platform.billing.stats.StatsAggregator;
import com.platform.billing.stats.StatsSnapshot;
import com.platform.common.model.ServiceInvokeLog;
import com.xxl.job.core.handler.annotation.XxlJob;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

public class StatsAggregatorJobHandler {
    private final StatsAggregator statsAggregator;
    private final Supplier<List<ServiceInvokeLog>> logSupplier;

    public StatsAggregatorJobHandler(StatsAggregator statsAggregator, Supplier<List<ServiceInvokeLog>> logSupplier) {
        this.statsAggregator = statsAggregator;
        this.logSupplier = logSupplier;
    }

    public List<StatsSnapshot> execute(Instant snapshotAt) {
        return statsAggregator.aggregate(logSupplier.get(), snapshotAt);
    }

    @XxlJob("statsAggregate")
    public void statsAggregate() {
        execute(Instant.now());
    }
}
