package com.platform.pipeline.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class IngestServiceJdbcTest {
    private JdbcTemplate jdbc;
    private IngestService service;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:ingest_jdbc;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        jdbc = new JdbcTemplate(ds);
        Flyway.configure().dataSource(ds).locations("filesystem:../db/migration").load().migrate();
        jdbc.update("DELETE FROM t_ingest_task");
        service = new IngestService(new HttpAdapter(), new JsonConverter(), new RawDataRepository(),
                IngestQualityGuard.disabled(), jdbc);
    }

    @Test
    void createTaskPersistsToDb() {
        IngestTask task = service.createTask(1L, URI.create("http://example.com/api"));
        IngestTask loaded = service.detail(task.id());
        assertEquals(task.id(), loaded.id());
        assertEquals("HTTP", loaded.protocol());
        assertEquals("JSON", loaded.format());
    }

    @Test
    void createTaskWithMappingPersistsAllFields() {
        IngestTask task = service.createTask(1L, URI.create("http://example.com/api"),
                "FULL", "0 * * * *", Map.of("src", "dst"), List.of("rule1", "rule2"));
        IngestTask loaded = service.detail(task.id());
        assertEquals("FULL", loaded.syncMode());
        assertEquals("0 * * * *", loaded.cronExpression());
        assertEquals(List.of("rule1", "rule2"), loaded.qualityRules());
    }

    @Test
    void listFiltersByPartner() {
        service.createTask(1L, URI.create("http://a.com"));
        service.createTask(2L, URI.create("http://b.com"));
        List<IngestTask> result = service.list(1L, null);
        assertEquals(1, result.size());
    }

    @Test
    void restartRecoveryDataSurvivesNewServiceInstance() {
        IngestTask task = service.createTask(1L, URI.create("http://example.com/api"),
                "INCREMENTAL", "0 0 * * *", Map.of("a", "b"), List.of("q1"));
        long taskId = task.id();

        IngestService restarted = new IngestService(new HttpAdapter(), new JsonConverter(),
                new RawDataRepository(), IngestQualityGuard.disabled(), jdbc);
        IngestTask loaded = restarted.detail(taskId);
        assertNotNull(loaded);
        assertEquals("INCREMENTAL", loaded.syncMode());
        assertEquals("0 0 * * *", loaded.cronExpression());
    }
}