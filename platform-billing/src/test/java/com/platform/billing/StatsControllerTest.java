package com.platform.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.platform.billing.bill.InMemoryBillRepository;
import com.platform.billing.dashboard.DashboardService;
import com.platform.billing.report.ReportGenerator;
import com.platform.billing.report.InMemoryRegulatoryReportRepository;
import com.platform.billing.report.RegulatoryReportService;
import com.platform.billing.stats.AuditTraceService;
import com.platform.billing.stats.InMemoryStatsSnapshotRepository;
import com.platform.common.audit.AuditEvent;
import com.platform.common.audit.AuditStatus;
import com.platform.common.audit.InMemoryAuditLogRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class StatsControllerTest {
    @Test
    void dashboardReturnsSummary() {
        var snapshotRepository = new InMemoryStatsSnapshotRepository();
        var billRepository = new InMemoryBillRepository();
        var auditLogRepository = new InMemoryAuditLogRepository();
        var regulatoryReportService = new RegulatoryReportService((from, to) -> java.util.List.of(),
                new InMemoryRegulatoryReportRepository(),
                report -> new com.platform.billing.regulatory.RegulatorySubmitResult(true, "REG-" + report.type().name(), "ok"),
                auditLogRepository);
        StatsController controller = new StatsController(snapshotRepository, billRepository,
                new DashboardService(), new ReportGenerator(), new AuditTraceService(auditLogRepository), null,
                regulatoryReportService);

        assertEquals(0L, controller.dashboard().data().invokeCount());
        assertEquals(0, controller.audit("PARTNER", null, null, null).data().size());
    }

    @Test
    void auditSupportsTraceQueryAndVerify() {
        var auditLogRepository = new InMemoryAuditLogRepository();
        var regulatoryReportService = new RegulatoryReportService((from, to) -> java.util.List.of(),
                new InMemoryRegulatoryReportRepository(),
                report -> new com.platform.billing.regulatory.RegulatorySubmitResult(true, "REG-" + report.type().name(), "ok"),
                auditLogRepository);
        auditLogRepository.append(new AuditEvent(null, "trace-stats", "CATALOG_APPLY", "USER", "u1",
                "CATALOG_APPLICATION", "1", "PENDING", "detail", "", "", AuditStatus.SUCCESS, Instant.now()));
        auditLogRepository.append(new AuditEvent(null, "trace-stats", "CATALOG_APPROVE", "USER", "admin",
                "CATALOG_APPLICATION", "1", "APPROVED", "detail", "", "", AuditStatus.SUCCESS, Instant.now().plusMillis(1)));
        StatsController controller = new StatsController(new InMemoryStatsSnapshotRepository(), new InMemoryBillRepository(),
                new DashboardService(), new ReportGenerator(), new AuditTraceService(auditLogRepository), null,
                regulatoryReportService);

        assertEquals(2, controller.audit(null, "trace-stats", null, null).data().size());
        assertTrue(controller.verifyAudit().data().intact());
    }
}
