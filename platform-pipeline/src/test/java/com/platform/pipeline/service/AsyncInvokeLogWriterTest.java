package com.platform.pipeline.service;

import com.platform.common.log.JdbcServiceInvokeLogRepository;
import com.platform.common.model.ServiceInvokeLog;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsyncInvokeLogWriterTest {
    @Test
    void sendsToKafkaWhenKafkaIsAvailable() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        AsyncInvokeLogWriter writer = new AsyncInvokeLogWriter(kafkaTemplate, "invoke-log-test");

        writer.write(sample("trace-kafka"));

        verify(kafkaTemplate).send(anyString(), anyString(), anyString());
        assertEquals(0, writer.logs().size());
    }

    @Test
    void fallsBackToJdbcWhenKafkaSendFails() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenThrow(new IllegalStateException("kafka down"));
        JdbcServiceInvokeLogRepository repository = new JdbcServiceInvokeLogRepository(jdbcTemplate("invoke-log-kafka-fallback"));
        AsyncInvokeLogWriter writer = new AsyncInvokeLogWriter(kafkaTemplate, "invoke-log-test", repository);

        writer.write(sample("trace-jdbc-fallback"));

        assertEquals("trace-jdbc-fallback", repository.findByRange(
                Instant.parse("2026-06-29T23:59:59Z"), Instant.parse("2026-06-30T00:00:01Z"), 1, 10)
                .records().get(0).traceId());
    }

    @Test
    void throwsWhenKafkaFailsAndNoRepositoryExists() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenThrow(new IllegalStateException("kafka down"));
        AsyncInvokeLogWriter writer = new AsyncInvokeLogWriter(kafkaTemplate, "invoke-log-test");

        assertThrows(IllegalStateException.class, () -> writer.write(sample("trace-no-repository")));
    }

    @Test
    void writesJdbcDirectlyWhenKafkaIsDisabledAndRepositoryExists() {
        JdbcServiceInvokeLogRepository repository = new JdbcServiceInvokeLogRepository(jdbcTemplate("invoke-log-jdbc-direct"));
        AsyncInvokeLogWriter writer = new AsyncInvokeLogWriter(null, "invoke-log-test", repository);

        writer.write(sample("trace-jdbc-direct"));

        assertEquals(1, repository.findByRange(
                Instant.parse("2026-06-29T23:59:59Z"), Instant.parse("2026-06-30T00:00:01Z"), 1, 10).total());
    }

    @Test
    void writesLocalMirrorWhenKafkaAndRepositoryAreDisabled() {
        AsyncInvokeLogWriter writer = new AsyncInvokeLogWriter();

        writer.write(sample("trace-local"));

        assertEquals("trace-local", writer.logs().get(0).traceId());
    }

    private ServiceInvokeLog sample(String traceId) {
        return new ServiceInvokeLog(traceId, "svc-fault", "consumer-fault", "partner-fault", "ak", "hash",
                200, 12, 128, null, null, Instant.parse("2026-06-30T00:00:00Z"));
    }

    private JdbcTemplate jdbcTemplate(String name) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource(name));
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
        return jdbcTemplate;
    }

    private JdbcDataSource dataSource(String name) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        return dataSource;
    }
}
