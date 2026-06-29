package com.platform.billing.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.platform.billing.regulatory.RegulatoryReportingAdapter;
import com.platform.billing.regulatory.RegulatorySubmitResult;
import com.platform.common.audit.AuditEvent;
import com.platform.common.audit.AuditLogRepository;
import com.platform.common.audit.AuditStatus;
import com.platform.common.exception.BusinessException;
import com.platform.common.model.ServiceInvokeLog;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class RegulatoryReportService {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final Supplier<List<ServiceInvokeLog>> invokeLogs;
    private final RegulatoryReportRepository repository;
    private final RegulatoryReportingAdapter reportingAdapter;
    private final AuditLogRepository auditLogRepository;

    public RegulatoryReportService(Supplier<List<ServiceInvokeLog>> invokeLogs,
                                   RegulatoryReportRepository repository,
                                   RegulatoryReportingAdapter reportingAdapter,
                                   AuditLogRepository auditLogRepository) {
        this.invokeLogs = invokeLogs;
        this.repository = repository;
        this.reportingAdapter = reportingAdapter;
        this.auditLogRepository = auditLogRepository;
    }

    public RegulatoryReportRecord generate(String reportType, Instant from, Instant to) {
        ReportType type = parseType(reportType);
        String auditTraceId = UUID.randomUUID().toString();
        List<ServiceInvokeLog> matched = invokeLogs.get().stream()
                .filter(log -> from == null || !log.createdAt().isBefore(from))
                .filter(log -> to == null || !log.createdAt().isAfter(to))
                .toList();
        String content = serialize(payload(type, from, to, matched));
        RegulatoryReportRecord saved = repository.save(new RegulatoryReportRecord(0, type.name(), from, to,
                content, "PENDING", null, null, Instant.now(), null));
        appendAudit(auditTraceId, saved.id(), "REGULATORY_REPORT_GENERATE", "GENERATE", AuditStatus.SUCCESS,
                "reportType=" + type.name());

        RegulatoryReportRecord submitted = submit(saved, type);
        appendAudit(auditTraceId, saved.id(), "REGULATORY_REPORT_SUBMIT", "SUBMIT",
                "SUBMITTED".equals(submitted.status()) ? AuditStatus.SUCCESS : AuditStatus.FAILED,
                "status=" + submitted.status() + ",receiptNo=" + nullToEmpty(submitted.receiptNo()));
        return submitted;
    }

    public List<RegulatoryReportRecord> list(String reportType) {
        String normalized = reportType == null || reportType.isBlank() ? null : parseType(reportType).name();
        return repository.findByType(normalized);
    }

    public RegulatoryReportRecord detail(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException("REGULATORY-404", "regulatory report not found"));
    }

    public byte[] export(long id) {
        return detail(id).content().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private RegulatoryReportRecord submit(RegulatoryReportRecord saved, ReportType type) {
        try {
            RegulatorySubmitResult result = reportingAdapter.submit(new GeneratedReport(type,
                    Path.of("target", "regulatory-report-" + saved.id() + ".json")));
            String status = result.success() ? "SUBMITTED" : "FAILED";
            return repository.updateSubmission(saved.id(), status, result.receiptNo(), result.message());
        } catch (Exception ex) {
            return repository.updateSubmission(saved.id(), "FAILED", null, ex.getMessage());
        }
    }

    private Map<String, Object> payload(ReportType type, Instant from, Instant to, List<ServiceInvokeLog> matched) {
        long successCount = matched.stream().filter(log -> log.status() >= 200 && log.status() < 300).count();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("invokeCount", matched.size());
        summary.put("successCount", successCount);
        summary.put("failCount", matched.size() - successCount);

        List<Map<String, Object>> details = matched.stream()
                .map(log -> detail(type, log))
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reportType", type.name());
        payload.put("periodFrom", from);
        payload.put("periodTo", to);
        payload.put("summary", summary);
        payload.put("details", details);
        return payload;
    }

    private Map<String, Object> detail(ReportType type, ServiceInvokeLog log) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("traceId", maskTrace(type, log.traceId()));
        detail.put("serviceCode", log.serviceCode());
        detail.put("consumerCode", maskConsumer(type, log.consumerCode()));
        detail.put("partnerCode", log.partnerCode());
        detail.put("status", log.status());
        return detail;
    }

    private ReportType parseType(String reportType) {
        try {
            return ReportType.valueOf(reportType);
        } catch (Exception ex) {
            throw new BusinessException("REGULATORY-400", "invalid reportType: " + reportType);
        }
    }

    private String serialize(Map<String, Object> payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new BusinessException("REGULATORY-500", "failed to serialize regulatory report");
        }
    }

    private String maskConsumer(ReportType type, String value) {
        return type == ReportType.PERSONAL_INFO ? maskMiddle(value) : value;
    }

    private String maskTrace(ReportType type, String value) {
        return type == ReportType.PERSONAL_INFO ? maskHeadTail(value, 4, 4) : value;
    }

    private String maskMiddle(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (value.length() <= 2) {
            return value.charAt(0) + "***";
        }
        return value.charAt(0) + "***" + value.charAt(value.length() - 1);
    }

    private String maskHeadTail(String value, int head, int tail) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (value.length() <= head + tail) {
            return "***";
        }
        return value.substring(0, head) + "***" + value.substring(value.length() - tail);
    }

    private void appendAudit(String traceId, long reportId, String eventType, String action, AuditStatus status, String detail) {
        auditLogRepository.append(new AuditEvent(null, traceId, eventType, "SYSTEM", "system",
                "REGULATORY_REPORT", String.valueOf(reportId), action, detail, "", "", status, Instant.now()));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
