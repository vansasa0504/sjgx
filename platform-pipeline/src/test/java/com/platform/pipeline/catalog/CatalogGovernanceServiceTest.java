package com.platform.pipeline.catalog;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CatalogGovernanceServiceTest {
    private JdbcTemplate jdbc;
    private CatalogService catalogService;
    private CatalogApplicationRepository applicationRepository;
    private CatalogGovernanceService governanceService;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:catalog_governance;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        jdbc = new JdbcTemplate(ds);
        Flyway.configure().dataSource(ds).locations("filesystem:../db/migration").load().migrate();
        jdbc.update("DELETE FROM t_service_invoke_log");
        jdbc.update("DELETE FROM t_catalog_application");
        jdbc.update("DELETE FROM t_catalog_quality_summary");
        jdbc.update("DELETE FROM t_catalog_lineage");
        jdbc.update("DELETE FROM t_data_catalog");

        CatalogLineageRepository lineageRepository = new JdbcCatalogLineageRepository(jdbc);
        CatalogQualitySummaryRepository qualityRepository = new JdbcCatalogQualitySummaryRepository(jdbc);
        applicationRepository = new JdbcCatalogApplicationRepository(jdbc);
        catalogService = new CatalogService(jdbc, lineageRepository, qualityRepository);
        governanceService = new CatalogGovernanceService(catalogService, lineageRepository,
                qualityRepository, applicationRepository, jdbc);
    }

    @Test
    void detailAggregatesMetaLineageQualityAndUsage() {
        DataCatalogItem item = catalogService.add("cat-governance", "治理资产", "征信", 12L,
                "CREDIT", "风控", List.of("name", "score"), "JSON", "DAILY", "TEST", "L2", "内部");
        catalogService.bindIngestTask(item.id(), 34L, "ingest-34");
        catalogService.bindService(item.id(), "svc-governance", "治理服务");
        catalogService.upsertQualitySummary(item.id(), 97.5, 2);
        applicationRepository.create(item.id(), "consumer-a", "reason", "svc-governance");
        insertInvokeLog(1L, "svc-governance");
        insertInvokeLog(2L, "svc-governance");
        insertInvokeLog(3L, "svc-other");

        CatalogDetail detail = governanceService.detail(item.id());

        assertEquals("cat-governance", detail.meta().catalogCode());
        assertEquals(3, detail.lineage().size());
        assertEquals(97.5, detail.quality().score());
        assertEquals(2, detail.quality().issueCount());
        assertEquals(2L, detail.usage().invokeCount());
        assertEquals(1L, detail.usage().applicationCount());
    }

    @Test
    void defaultsEmptyGovernanceDataToZeroValues() {
        DataCatalogItem item = catalogService.add("cat-empty", "空治理资产", "政务", 13L,
                "GOV", "营销", List.of("name"), "JSON", "DAILY", "TEST", "L1", "内部");

        CatalogUsageSummary usage = governanceService.usageSummary(item.id());

        assertEquals(0L, usage.invokeCount());
        assertEquals(0L, usage.applicationCount());
        assertEquals(0.0, governanceService.qualitySummary(item.id()).score());
    }

    private void insertInvokeLog(long id, String serviceCode) {
        jdbc.update("""
                INSERT INTO t_service_invoke_log
                (id, service_code, consumer_code, status_code, elapsed_millis, log_day, created_at, response_size)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?)
                """, id, serviceCode, "consumer-a", 200, 12L, "20260629", 128L);
    }
}
