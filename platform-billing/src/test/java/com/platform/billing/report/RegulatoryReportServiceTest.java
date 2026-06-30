package com.platform.billing.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.platform.billing.regulatory.RegulatorySubmitResult;
import com.platform.common.audit.AuditEvent;
import com.platform.common.audit.InMemoryAuditLogRepository;
import com.platform.common.exception.BusinessException;
import com.platform.common.model.ServiceInvokeLog;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegulatoryReportServiceTest {
    private static final Instant T1 = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-06-02T00:00:00Z");

    @Test
    void generatePersistsSubmittedReceiptAndMasksPersonalInfo() {
        InMemoryRegulatoryReportRepository repository = new InMemoryRegulatoryReportRepository();
        InMemoryAuditLogRepository auditRepository = new InMemoryAuditLogRepository();
        RegulatoryReportService service = new RegulatoryReportService((from, to) -> logs(), repository,
                report -> new RegulatorySubmitResult(true, "REG-PERSONAL_INFO", "ok"), auditRepository);

        RegulatoryReportRecord record = service.generate("PERSONAL_INFO", T1, T2);

        assertEquals("SUBMITTED", record.status());
        assertEquals("REG-PERSONAL_INFO", record.receiptNo());
        assertTrue(record.content().contains("\"consumerCode\":\"C***1\""));
        assertTrue(record.content().contains("\"traceId\":\"trac***0001\""));
        assertFalse(record.content().contains("CONSUMER-1"));
        assertFalse(record.content().contains("api-secret"));
        assertFalse(record.content().contains("apiKey"));
        assertEquals(1, repository.findByType("PERSONAL_INFO").size());
        List<AuditEvent> generateEvents = auditRepository.findByEventType("REGULATORY_REPORT_GENERATE", Instant.EPOCH, Instant.now());
        List<AuditEvent> submitEvents = auditRepository.findByEventType("REGULATORY_REPORT_SUBMIT", Instant.EPOCH, Instant.now());
        assertEquals(1, generateEvents.size());
        assertEquals(1, submitEvents.size());
        assertEquals(generateEvents.get(0).traceId(), submitEvents.get(0).traceId());
    }

    @Test
    void complianceReportDoesNotMaskAndFailureReceiptIsPersisted() {
        RegulatoryReportService service = new RegulatoryReportService((from, to) -> logs(),
                new InMemoryRegulatoryReportRepository(),
                report -> new RegulatorySubmitResult(false, null, "remote rejected"),
                new InMemoryAuditLogRepository());

        RegulatoryReportRecord record = service.generate("COMPLIANCE", null, null);

        assertEquals("FAILED", record.status());
        assertEquals("remote rejected", record.receiptMessage());
        assertTrue(record.content().contains("CONSUMER-1"));
        assertFalse(record.content().contains("api-secret"));
        assertFalse(record.content().contains("apiKey"));
        assertTrue(record.content().contains("\"invokeCount\":2"));
        assertTrue(record.content().contains("\"successCount\":1"));
        assertTrue(record.content().contains("\"failCount\":1"));
    }

    @Test
    void timeRangeFiltersLogsAndInvalidTypeReturnsBusinessError() {
        RegulatoryReportService service = new RegulatoryReportService((from, to) -> logs(),
                new InMemoryRegulatoryReportRepository(),
                report -> new RegulatorySubmitResult(true, "REG-DATA_SOURCE", "ok"),
                new InMemoryAuditLogRepository());

        RegulatoryReportRecord record = service.generate("DATA_SOURCE", T2, T2);

        assertTrue(record.content().contains("\"invokeCount\":1"));
        BusinessException ex = org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> service.generate("BAD", null, null));
        assertEquals("REGULATORY-400", ex.code());
    }

    private List<ServiceInvokeLog> logs() {
        return List.of(
                new ServiceInvokeLog("trace-0001", "svc-a", "CONSUMER-1", "partner-a", "api-secret",
                        "hash", 200, 12L, 128L, null, null, T1),
                new ServiceInvokeLog("trace-0002", "svc-b", "CONSUMER-2", "partner-b", "api-secret-2",
                        "hash2", 500, 30L, 0L, "ERR", "failed", T2));
    }
}
