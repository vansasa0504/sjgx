package com.platform.billing.finance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class FinanceSyncRepositoryJdbcTest {
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:finance_sync_repo;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_finance_sync_record");
        jdbcTemplate.execute("""
                CREATE TABLE t_finance_sync_record (
                    id BIGINT PRIMARY KEY,
                    bill_no VARCHAR(64) NOT NULL,
                    adapter_type VARCHAR(32) NOT NULL,
                    external_no VARCHAR(128),
                    status VARCHAR(32) NOT NULL,
                    retry_count INT NOT NULL,
                    message VARCHAR(512),
                    synced_at TIMESTAMP NOT NULL
                )
                """);
    }

    @Test
    void saveFindByBillNoAndFindLastFailedWithNewRepositoryInstance() {
        JdbcFinanceSyncRepository repository = new JdbcFinanceSyncRepository(jdbcTemplate);
        repository.save(new FinanceSyncRecord(0, "BILL-J", "FINANCE", null,
                "FAILED", 0, "rejected", Instant.parse("2026-06-01T00:00:00Z")));
        repository.save(new FinanceSyncRecord(0, "BILL-J", "FINANCE", null,
                "FAILED", 1, "still rejected", Instant.parse("2026-06-02T00:00:00Z")));
        repository.save(new FinanceSyncRecord(0, "BILL-J", "PURCHASE", "PUR-BILL-J",
                "SUCCESS", 0, "ok", Instant.parse("2026-06-03T00:00:00Z")));

        JdbcFinanceSyncRepository restarted = new JdbcFinanceSyncRepository(jdbcTemplate);

        assertEquals(3, restarted.findByBillNo("BILL-J").size());
        FinanceSyncRecord lastFailed = restarted.findLastFailed("BILL-J", "FINANCE").orElseThrow();
        assertEquals(1, lastFailed.retryCount());
        assertTrue(restarted.findLastFailed("BILL-J", "PURCHASE").isEmpty());
    }
}
