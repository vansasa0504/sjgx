package com.platform.common.partition;

import java.time.Duration;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;

public class PartitionMaintenanceJob {
    private final PartitionMaintainer maintainer;
    private final int monthsAhead;
    private final Duration retention;

    public PartitionMaintenanceJob(PartitionMaintainer maintainer, int monthsAhead, Duration retention) {
        this.maintainer = maintainer;
        this.monthsAhead = monthsAhead;
        this.retention = retention;
    }

    @Scheduled(cron = "${platform.partition.maintain.cron:0 0 2 * * ?}")
    public void maintain() {
        Instant cutoff = Instant.now().minus(retention);
        for (String table : java.util.List.of("t_service_invoke_log", "t_audit_log", "t_raw_data")) {
            maintainer.ensureFuturePartitions(table, monthsAhead);
        }
        maintainer.archiveExpiredPartitions("t_service_invoke_log", cutoff);
        maintainer.dropExpiredPartitions("t_service_invoke_log", cutoff);
        maintainer.archiveExpiredPartitions("t_audit_log", cutoff);
    }
}
