package com.platform.quality.report;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class QualityReportRepositoryJdbcTest {
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:quality_report_jdbc;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        jdbc = new JdbcTemplate(ds);
        Flyway.configure().dataSource(ds).locations("filesystem:../db/migration").load().migrate();
        jdbc.update("DELETE FROM t_quality_report");
    }

    @Test
    void savePersistsAndAssignsId() {
        JdbcQualityReportRepository repo = new JdbcQualityReportRepository(jdbc);

        QualityReportRecord saved = repo.save(new QualityReportRecord(0, "PARTNER", "p1",
                5, 3, 2, 0.4, 60.0, Instant.now()));

        assertTrue(saved.id() > 0);

        JdbcQualityReportRepository restarted = new JdbcQualityReportRepository(jdbc);
        Optional<QualityReportRecord> loaded = restarted.findById(saved.id());
        assertTrue(loaded.isPresent());
        assertEquals("PARTNER", loaded.get().dimension());
        assertEquals("p1", loaded.get().dimensionValue());
        assertEquals(5, loaded.get().checkCount());
        assertEquals(2, loaded.get().failCount());
        assertEquals(0.4, loaded.get().failRate(), 0.001);
        assertEquals(60.0, loaded.get().score(), 0.01);
    }

    @Test
    void findByDimensionFiltersAndOrders() {
        JdbcQualityReportRepository repo = new JdbcQualityReportRepository(jdbc);

        repo.save(new QualityReportRecord(0, "PARTNER", "p1", 3, 3, 0, 0.0, 100.0, Instant.now()));
        repo.save(new QualityReportRecord(0, "ASSET", "a1", 2, 1, 1, 0.5, 50.0, Instant.now()));
        repo.save(new QualityReportRecord(0, "PARTNER", "p2", 4, 2, 2, 0.5, 50.0, Instant.now()));

        List<QualityReportRecord> partnerReports = repo.findByDimension("PARTNER");
        assertEquals(2, partnerReports.size());
        assertTrue(partnerReports.stream().allMatch(r -> "PARTNER".equals(r.dimension())));

        List<QualityReportRecord> allReports = repo.findByDimension(null);
        assertEquals(3, allReports.size());
    }

    @Test
    void findByIdReturnsEmptyWhenMissing() {
        JdbcQualityReportRepository repo = new JdbcQualityReportRepository(jdbc);
        assertTrue(repo.findById(99999L).isEmpty());
    }
}
