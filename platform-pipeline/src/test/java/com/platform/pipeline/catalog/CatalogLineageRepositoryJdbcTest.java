package com.platform.pipeline.catalog;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CatalogLineageRepositoryJdbcTest {
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:catalog_lineage_jdbc;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        jdbc = new JdbcTemplate(ds);
        Flyway.configure().dataSource(ds).locations("filesystem:../db/migration").load().migrate();
        jdbc.update("DELETE FROM t_catalog_quality_summary");
        jdbc.update("DELETE FROM t_catalog_lineage");
    }

    @Test
    void persistsLineageAcrossRepositoryRestartAndDeduplicates() {
        JdbcCatalogLineageRepository first = new JdbcCatalogLineageRepository(jdbc);
        first.save(new CatalogLineage(7L, CatalogLineage.PARTNER, 3L, "partner-a", CatalogLineage.UPSTREAM));
        first.save(new CatalogLineage(7L, CatalogLineage.PARTNER, 3L, "partner-renamed", CatalogLineage.UPSTREAM));
        first.save(new CatalogLineage(7L, CatalogLineage.DATA_SERVICE, 9L, "svc-risk", CatalogLineage.DOWNSTREAM));

        JdbcCatalogLineageRepository restarted = new JdbcCatalogLineageRepository(jdbc);

        assertEquals(2, restarted.findByCatalogId(7L).size());
        assertEquals("partner-renamed", restarted.findByCatalogId(7L).stream()
                .filter(lineage -> CatalogLineage.PARTNER.equals(lineage.nodeType()))
                .findFirst().orElseThrow().nodeName());
    }

    @Test
    void qualitySummaryUpsertPersistsLatestValue() {
        JdbcCatalogQualitySummaryRepository repository = new JdbcCatalogQualitySummaryRepository(jdbc);

        repository.upsert(7L, 91.5, 3);
        repository.upsert(7L, 98.0, 1);

        CatalogQualitySummary loaded = new JdbcCatalogQualitySummaryRepository(jdbc)
                .findByCatalogId(7L).orElseThrow();
        assertEquals(98.0, loaded.score());
        assertEquals(1, loaded.issueCount());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_catalog_quality_summary WHERE catalog_id = ?",
                Integer.class, 7L));
    }
}
