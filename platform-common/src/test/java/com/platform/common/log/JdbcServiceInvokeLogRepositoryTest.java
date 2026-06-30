package com.platform.common.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.platform.common.model.ServiceInvokeLog;
import java.time.Instant;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcServiceInvokeLogRepositoryTest {
    @Test
    void savesAndQueriesByConsumerAndService() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
        createTable(jdbcTemplate);
        JdbcServiceInvokeLogRepository repository = new JdbcServiceInvokeLogRepository(jdbcTemplate);
        Instant now = Instant.parse("2026-06-28T00:00:00Z");

        repository.save(new ServiceInvokeLog("trace-1", "svc-a", "c1", "p1", "ak", "hash-1",
                200, 15, 128, null, null, now));
        repository.save(new ServiceInvokeLog("trace-2", "svc-a", "c2", "p1", "ak", "hash-2",
                500, 20, 0, "SERVICE-500", "route failed", now.plusSeconds(1)));

        assertEquals(1, repository.findByConsumer("c1", 1, 10).total());
        assertEquals("trace-1", repository.findByService("svc-a", "c1", "200", 1, 10).records().get(0).traceId());
        assertEquals("SERVICE-500", repository.findByService("svc-a", null, "500", 1, 10).records().get(0).errorCode());
        assertEquals(2, repository.findByRange(now.minusSeconds(1), now.plusSeconds(2), 1, 10).total());
        assertEquals(1, repository.findByRange(now.plusMillis(500), now.plusSeconds(2), 1, 10).total());
        assertEquals("trace-2", repository.findByServiceRange("svc-a", null, null,
                now.plusMillis(500), now.plusSeconds(2), 1, 10).records().get(0).traceId());
        assertTrue(repository.findAllByRange(now.minusSeconds(1), now.plusSeconds(2)).stream()
                .anyMatch(log -> "trace-1".equals(log.traceId())));
    }

    private void createTable(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE t_service_invoke_log (
                    id BIGINT PRIMARY KEY,
                    trace_id VARCHAR(64),
                    service_code VARCHAR(64) NOT NULL,
                    consumer_code VARCHAR(64) NOT NULL,
                    partner_code VARCHAR(64),
                    api_key VARCHAR(128),
                    request_hash VARCHAR(128),
                    status_code INT NOT NULL,
                    elapsed_millis BIGINT NOT NULL,
                    response_size BIGINT DEFAULT 0 NOT NULL,
                    error_code VARCHAR(64),
                    error_message VARCHAR(512),
                    log_day VARCHAR(8) NOT NULL,
                    created_at TIMESTAMP NOT NULL
                )
                """);
    }

    private JdbcDataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:invoke-log-repo;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        return dataSource;
    }
}
