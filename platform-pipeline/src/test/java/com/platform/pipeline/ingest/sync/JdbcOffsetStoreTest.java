package com.platform.pipeline.ingest.sync;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JdbcOffsetStoreTest {
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:jdbc_offset_store;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        jdbcTemplate = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).locations("filesystem:../db/migration").load().migrate();
        jdbcTemplate.update("DELETE FROM t_ingest_checkpoint");
    }

    @Test
    void persistsOffsetAndRestoresAfterNewStoreInstance() {
        JdbcOffsetStore store = new JdbcOffsetStore(jdbcTemplate);

        store.put("10:HTTP", 5L);
        store.put("10:HTTP", 3L);

        JdbcOffsetStore restarted = new JdbcOffsetStore(jdbcTemplate);
        assertEquals(5L, restarted.get("10:HTTP"));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM t_ingest_checkpoint
                WHERE task_id = ? AND connector_type = ?
                """, Integer.class, 10L, "HTTP"));
    }
}
