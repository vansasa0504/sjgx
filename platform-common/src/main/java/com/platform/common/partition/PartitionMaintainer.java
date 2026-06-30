package com.platform.common.partition;

import java.time.Instant;

public interface PartitionMaintainer {
    void ensureFuturePartitions(String table, int monthsAhead);

    void archiveExpiredPartitions(String table, Instant cutoff);

    void dropExpiredPartitions(String table, Instant cutoff);
}
