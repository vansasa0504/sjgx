package com.platform.pipeline.catalog;

import com.platform.common.exception.BusinessException;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogApplicationRepositoryJdbcTest {
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:catalog_application_jdbc;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        jdbc = new JdbcTemplate(ds);
        Flyway.configure().dataSource(ds).locations("filesystem:../db/migration").load().migrate();
        jdbc.update("DELETE FROM t_catalog_application");
    }

    @Test
    void persistsApplicationAcrossRepositoryRestart() {
        JdbcCatalogApplicationRepository first = new JdbcCatalogApplicationRepository(jdbc);
        CatalogApplication created = first.create(7L, "consumer-a", "风控申请", "svc-risk");

        JdbcCatalogApplicationRepository restarted = new JdbcCatalogApplicationRepository(jdbc);
        CatalogApplication loaded = restarted.findById(created.id()).orElseThrow();

        assertEquals(created.id(), loaded.id());
        assertEquals(7L, loaded.catalogId());
        assertEquals("consumer-a", loaded.applicant());
        assertEquals("PENDING", loaded.status());
    }

    @Test
    void approvesOnlyPendingApplications() {
        JdbcCatalogApplicationRepository repository = new JdbcCatalogApplicationRepository(jdbc);
        CatalogApplication created = repository.create(7L, "consumer-a", "风控申请", "svc-risk");

        CatalogApplication approved = repository.approve(created.id(), "admin");

        assertEquals("APPROVED", approved.status());
        assertEquals("admin", approved.approver());
        assertTrue(repository.hasApproved(7L, "consumer-a"));
        BusinessException ex = assertThrows(BusinessException.class, () -> repository.reject(created.id(), "admin"));
        assertEquals("CATALOG_APP-409", ex.code());
    }
}
