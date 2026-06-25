package com.platform.billing.stats;

import java.util.List;

public interface StatsSnapshotRepository {
    StatsSnapshot save(StatsSnapshot snapshot);

    List<StatsSnapshot> findAll();
}