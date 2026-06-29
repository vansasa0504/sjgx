package com.platform.billing;

import com.platform.billing.dashboard.DashboardService;
import com.platform.billing.dashboard.DashboardSummary;
import com.platform.billing.report.GeneratedReport;
import com.platform.billing.report.ReportGenerator;
import com.platform.billing.report.ReportType;
import com.platform.billing.stats.AuditTraceService;
import com.platform.billing.stats.StatsSnapshotRepository;
import com.platform.common.log.JdbcServiceInvokeLogRepository;
import com.platform.common.audit.AuditChainVerification;
import com.platform.common.audit.AuditEvent;
import com.platform.common.model.Result;
import com.platform.common.security.RequirePermission;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {
    private final StatsSnapshotRepository snapshotRepository;
    private final com.platform.billing.bill.BillRepository billRepository;
    private final DashboardService dashboardService;
    private final ReportGenerator reportGenerator;
    private final AuditTraceService auditTraceService;
    private final JdbcServiceInvokeLogRepository invokeLogRepository;

    public StatsController(StatsSnapshotRepository snapshotRepository,
                           com.platform.billing.bill.BillRepository billRepository,
                           DashboardService dashboardService,
                           ReportGenerator reportGenerator,
                           AuditTraceService auditTraceService,
                           @Autowired(required = false) JdbcServiceInvokeLogRepository invokeLogRepository) {
        this.snapshotRepository = snapshotRepository;
        this.billRepository = billRepository;
        this.dashboardService = dashboardService;
        this.reportGenerator = reportGenerator;
        this.auditTraceService = auditTraceService;
        this.invokeLogRepository = invokeLogRepository;
    }

    @GetMapping("/dashboard")
    @RequirePermission("stats:view")
    public Result<DashboardSummary> dashboard() {
        return Result.ok(dashboardService.summarize(snapshotRepository.findAll(), billRepository.findAll()));
    }

    @GetMapping("/reports")
    @RequirePermission("stats:view")
    public Result<GeneratedReport> report(@RequestParam ReportType type,
                                          @RequestParam(required = false) String from,
                                          @RequestParam(required = false) String to) {
        List<String> rows = invokeLogRepository == null ? List.of()
                : invokeLogRepository.findAll().stream()
                .map(log -> log.traceId() + "|" + log.serviceCode() + "|" + log.consumerCode() + "|" + log.status())
                .toList();
        return Result.ok(reportGenerator.generate(type, rows, Path.of("target/reports")));
    }

    @GetMapping("/audit")
    @RequirePermission("stats:view")
    public Result<List<AuditEvent>> audit(@RequestParam(required = false) String eventType,
                                          @RequestParam(required = false) String traceId,
                                          @RequestParam(required = false) Instant from,
                                          @RequestParam(required = false) Instant to) {
        if (traceId != null && !traceId.isBlank()) {
            return Result.ok(auditTraceService.byTrace(traceId));
        }
        Instant start = from == null ? Instant.EPOCH : from;
        Instant end = to == null ? Instant.now() : to;
        if (eventType == null || eventType.isBlank()) {
            return Result.ok(List.of());
        }
        return Result.ok(auditTraceService.byEventType(eventType, start, end));
    }

    @GetMapping("/audit/verify")
    @RequirePermission("stats:view")
    public Result<AuditChainVerification> verifyAudit() {
        return Result.ok(auditTraceService.verify());
    }
}
