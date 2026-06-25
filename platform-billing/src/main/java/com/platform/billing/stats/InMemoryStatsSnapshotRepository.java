package com.platform.billing.stats;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryStatsSnapshotRepository implements StatsSnapshotRepository {
    private final AtomicLong ids = new AtomicLong(1);
    private final List<StatsSnapshot> snapshots = new CopyOnWriteArrayList<>();

    @Override
    public StatsSnapshot save(StatsSnapshot snapshot) {
        StatsSnapshot saved = new StatsSnapshot(snapshot.id() == null ? ids.getAndIncrement() : snapshot.id(),
                snapshot.metricName(), snapshot.dimension(), snapshot.dimensionId(), snapshot.metricValue(), snapshot.snapshotAt());
        snapshots.add(saved);
        return saved;
    }

    @Override
    public List<StatsSnapshot> findAll() {
        return List.copyOf(snapshots);
    }
}