package com.platform.common.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class IdGeneratorTest {

    @Test
    void nextIdReturnsSequentialValues() {
        JdbcTemplate jdbc = migrate("id_seq");
        IdGenerator gen = new IdGenerator(jdbc);

        assertEquals(1L, gen.nextId("t_user"));
        assertEquals(2L, gen.nextId("t_user"));
        assertEquals(3L, gen.nextId("t_user"));
    }

    @Test
    void nextIdIsConcurrencySafe() throws InterruptedException {
        JdbcTemplate jdbc = migrate("id_concurrent");
        IdGenerator gen = new IdGenerator(jdbc);

        int threads = 20;
        int idsPerThread = 50;
        Set<Long> allIds = new HashSet<>();
        CountDownLatch latch = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                Set<Long> localIds = new HashSet<>();
                for (int j = 0; j < idsPerThread; j++) {
                    localIds.add(gen.nextId("t_partner"));
                }
                synchronized (allIds) {
                    allIds.addAll(localIds);
                }
                latch.countDown();
            });
        }
        latch.await();
        pool.shutdown();

        int expected = threads * idsPerThread;
        assertEquals(expected, allIds.size(), "concurrent IDs must be unique");
        for (long i = 1; i <= expected; i++) {
            assertTrue(allIds.contains(i), "missing ID: " + i);
        }
    }

    @Test
    void nextIdInitializesFromExistingData() {
        JdbcTemplate jdbc = migrate("id_init");
        jdbc.update("INSERT INTO t_user (id, username, password_hash, status, created_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)",
                100L, "existing", "hash", "ACTIVE");
        IdGenerator gen = new IdGenerator(jdbc);

        assertEquals(101L, gen.nextId("t_user"));
    }

    @Test
    void nextIdIndependentPerTable() {
        JdbcTemplate jdbc = migrate("id_multi");
        IdGenerator gen = new IdGenerator(jdbc);

        assertEquals(1L, gen.nextId("t_user"));
        assertEquals(1L, gen.nextId("t_partner"));
        assertEquals(2L, gen.nextId("t_user"));
    }

    private JdbcTemplate migrate(String name) {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        Flyway.configure().dataSource(ds).locations("filesystem:../db/migration").load().migrate();
        return jdbc;
    }
}