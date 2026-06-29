package com.platform.billing.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class RegulatoryReportRepositoryJdbcTest {
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:regulatory_report_repo;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_regulatory_report");
        jdbcTemplate.execute("""
                CREATE TABLE t_regulatory_report (
                    id BIGINT PRIMARY KEY,
                    report_type VARCHAR(32) NOT NULL,
                    period_from TIMESTAMP NULL,
                    period_to TIMESTAMP NULL,
                    content LONGTEXT NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    receipt_no VARCHAR(128),
                    receipt_message VARCHAR(512),
                    generated_at TIMESTAMP NOT NULL,
                    submitted_at TIMESTAMP NULL
                )
                """);
    }

    @Test
    void saveUpdateAndReadBackWithNewRepositoryInstance() {
        JdbcRegulatoryReportRepository repository = new JdbcRegulatoryReportRepository(jdbcTemplate);
        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-30T23:59:59Z");

        RegulatoryReportRecord saved = repository.save(new RegulatoryReportRecord(0, "COMPLIANCE", from, to,
                "{\"summary\":{}}", "PENDING", null, null, Instant.now(), null));
        RegulatoryReportRecord submitted = repository.updateSubmission(saved.id(), "SUBMITTED", "REG-1", "ok");
        JdbcRegulatoryReportRepository restarted = new JdbcRegulatoryReportRepository(jdbcTemplate);

        assertEquals("SUBMITTED", submitted.status());
        assertEquals("REG-1", restarted.findById(saved.id()).orElseThrow().receiptNo());
        assertEquals(1, restarted.findByType("COMPLIANCE").size());
        assertTrue(restarted.findByType(null).get(0).content().contains("summary"));
    }
}
