package com.platform.quality.report;

import com.platform.quality.executor.QualityCheckExecutor;
import com.platform.quality.rule.QualityDimension;
import com.platform.quality.rule.QualityRuleConfig;
import com.platform.quality.executor.QualityCheckResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QualityReportServiceTest {
    private QualityCheckExecutor executor;
    private InMemoryQualityReportRepository repository;
    private QualityReportService service;

    @BeforeEach
    void setUp() {
        executor = new QualityCheckExecutor();
        repository = new InMemoryQualityReportRepository();
        service = new QualityReportService(executor, repository);
    }

    @Test
    void generateAggregatesAndPersists() {
        QualityRuleConfig completeness = new QualityRuleConfig(
                "QR-COMPLETENESS", QualityDimension.COMPLETENESS, "name", Map.of(), 10);
        executor.check("BATCH-PARTNER-1", List.of(row("name", "alpha")), List.of(completeness), 0.0);
        executor.check("BATCH-PARTNER-1", List.of(row("name", "")), List.of(completeness), 0.0);
        executor.check("BATCH-PARTNER-2", List.of(row("name", "beta")), List.of(completeness), 0.0);

        QualityReportRecord record = service.generate("PARTNER", "BATCH-PARTNER-1", null, null);

        assertEquals("PARTNER", record.dimension());
        assertEquals("BATCH-PARTNER-1", record.dimensionValue());
        assertEquals(2, record.checkCount());
        assertEquals(1, record.failCount());
        assertEquals(1, record.passCount());
        assertEquals(0.5, record.failRate(), 0.001);
        assertEquals(50.0, record.score(), 0.01);
        assertTrue(record.id() > 0);

        QualityReportRecord reloaded = service.detail(record.id());
        assertEquals(record.id(), reloaded.id());
        assertEquals(50.0, reloaded.score(), 0.01);
    }

    @Test
    void noMatchesStillPersistsZeroStats() {
        QualityReportRecord record = service.generate("ASSET", "asset-999", null, null);

        assertEquals(0, record.checkCount());
        assertEquals(0, record.failCount());
        assertEquals(0.0, record.failRate());
        assertEquals(0.0, record.score());
        assertTrue(record.id() > 0);
    }

    @Test
    void listFiltersByDimension() {
        service.generate("PARTNER", "p1", null, null);
        service.generate("ASSET", "a1", null, null);
        service.generate("PARTNER", "p2", null, null);

        List<QualityReportRecord> partnerReports = service.list("PARTNER");

        assertEquals(2, partnerReports.size());
        assertTrue(partnerReports.stream().allMatch(r -> "PARTNER".equals(r.dimension())));
    }

    @Test
    void detailNotFoundThrows() {
        assertThrows(Exception.class, () -> service.detail(99999L));
    }

    @Test
    void exportReturnsSameAsDetail() {
        QualityReportRecord record = service.generate("SERVICE", "svc-a", null, null);
        QualityReportRecord exported = service.export(record.id());
        assertEquals(record.id(), exported.id());
        assertEquals("svc-a", exported.dimensionValue());
    }

    @Test
    void timeRangeFiltersHistory() {
        Instant before = Instant.now().minusSeconds(1);
        executor.check("BATCH-SVC-1", List.of(row("name", "ok")), List.of(), 0.0);
        Instant afterFirst = Instant.now().plusSeconds(1);

        QualityReportRecord record = service.generate("SERVICE", "BATCH-SVC-1", before, afterFirst);
        assertEquals(1, record.checkCount());

        QualityReportRecord emptyRecord = service.generate("SERVICE", "SVC-1",
                afterFirst.plusSeconds(10), afterFirst.plusSeconds(20));
        assertEquals(0, emptyRecord.checkCount());
    }

    private Map<String, Object> row(String field, String value) {
        return Map.of(field, value);
    }
}
