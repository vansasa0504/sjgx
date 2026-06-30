package com.platform.common.partition;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PartitionMaintenanceJobTest {
    @Test
    void auditLogIsArchivedButNeverDropped() {
        RecordingMaintainer maintainer = new RecordingMaintainer();
        PartitionMaintenanceJob job = new PartitionMaintenanceJob(maintainer, 1, Duration.ofDays(365));

        job.maintain();

        assertTrue(maintainer.calls.contains("archive:t_audit_log"));
        assertFalse(maintainer.calls.contains("drop:t_audit_log"));
        assertTrue(maintainer.calls.contains("archive:t_service_invoke_log"));
        assertTrue(maintainer.calls.contains("drop:t_service_invoke_log"));
        assertTrue(maintainer.calls.contains("ensure:t_raw_data"));
    }

    private static final class RecordingMaintainer implements PartitionMaintainer {
        private final List<String> calls = new ArrayList<>();

        @Override
        public void ensureFuturePartitions(String table, int monthsAhead) {
            calls.add("ensure:" + table);
        }

        @Override
        public void archiveExpiredPartitions(String table, Instant cutoff) {
            calls.add("archive:" + table);
        }

        @Override
        public void dropExpiredPartitions(String table, Instant cutoff) {
            calls.add("drop:" + table);
        }
    }
}
