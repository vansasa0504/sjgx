package com.platform.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.platform.billing.bill.InMemoryBillRepository;
import com.platform.billing.dashboard.DashboardService;
import com.platform.billing.report.ReportGenerator;
import com.platform.billing.stats.AuditTraceService;
import com.platform.billing.stats.InMemoryStatsSnapshotRepository;
import com.platform.common.audit.InMemoryAuditLogRepository;
import org.junit.jupiter.api.Test;

class StatsControllerTest {
    @Test
    void dashboardReturnsSummary() {
        var snapshotRepository = new InMemoryStatsSnapshotRepository();
        var billRepository = new InMemoryBillRepository();
        var auditLogRepository = new InMemoryAuditLogRepository();
        StatsController controller = new StatsController(snapshotRepository, billRepository,
                new DashboardService(), new ReportGenerator(), new AuditTraceService(auditLogRepository), null);

        assertEquals(0L, controller.dashboard().data().invokeCount());
        assertEquals(0, controller.audit("PARTNER", null, null).data().size());
    }
}
