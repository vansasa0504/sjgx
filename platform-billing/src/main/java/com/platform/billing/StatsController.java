package com.platform.billing;

import com.platform.billing.dashboard.DashboardService;
import com.platform.billing.dashboard.DashboardSummary;
import com.platform.billing.report.GeneratedReport;
import com.platform.billing.report.ReportGenerator;
import com.platform.billing.report.ReportType;
import com.platform.billing.report.RegulatoryReportRecord;
import com.platform.billing.report.RegulatoryReportService;
import com.platform.billing.stats.AuditTraceService;
import com.platform.billing.stats.StatsSnapshotRepository;
import com.platform.common.exception.BusinessException;
import com.platform.common.log.JdbcServiceInvokeLogRepository;
import com.platform.common.audit.AuditChainVerification;
import com.platform.common.audit.AuditEvent;
import com.platform.common.model.Result;
import com.platform.common.security.RequirePermission;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final RegulatoryReportService regulatoryReportService;

    public StatsController(StatsSnapshotRepository snapshotRepository,
                           com.platform.billing.bill.BillRepository billRepository,
                           DashboardService dashboardService,
                           ReportGenerator reportGenerator,
                           AuditTraceService auditTraceService,
                           @Autowired(required = false) JdbcServiceInvokeLogRepository invokeLogRepository,
                           RegulatoryReportService regulatoryReportService) {
        this.snapshotRepository = snapshotRepository;
        this.billRepository = billRepository;
        this.dashboardService = dashboardService;
        this.reportGenerator = reportGenerator;
        this.auditTraceService = auditTraceService;
        this.invokeLogRepository = invokeLogRepository;
        this.regulatoryReportService = regulatoryReportService;
    }

    @GetMapping("/dashboard")
    @RequirePermission("stats:view")
    public Result<DashboardSummary> dashboard() {
        return Result.ok(dashboardService.summarize(snapshotRepository.findAll(), billRepository.findAll()));
    }

    @GetMapping(value = "/reports", params = "type")
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

    @PostMapping("/reports/generate")
    @RequirePermission("billing:run")
    public Result<RegulatoryReportRecord> generateRegulatoryReport(@RequestBody GenerateRegulatoryReportRequest request) {
        return Result.ok(regulatoryReportService.generate(request.reportType(), parseInstant(request.from()), parseInstant(request.to())));
    }

    @GetMapping(value = "/reports", params = "!type")
    @RequirePermission("stats:view")
    public Result<List<RegulatoryReportRecord>> regulatoryReports(@RequestParam(required = false) String reportType) {
        return Result.ok(regulatoryReportService.list(reportType));
    }

    @GetMapping("/reports/{id}")
    @RequirePermission("stats:view")
    public Result<RegulatoryReportRecord> regulatoryReportDetail(@PathVariable long id) {
        return Result.ok(regulatoryReportService.detail(id));
    }

    @GetMapping("/reports/{id}/export")
    @RequirePermission("stats:view")
    public ResponseEntity<byte[]> exportRegulatoryReport(@PathVariable long id) {
        byte[] content = regulatoryReportService.export(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=regulatory-report-" + id + ".json");
        return ResponseEntity.ok().headers(headers).body(content);
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

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeException ex) {
            throw new BusinessException("REGULATORY-400", "invalid timestamp: " + value);
        }
    }

    public record GenerateRegulatoryReportRequest(String reportType, String from, String to) {
    }
}
