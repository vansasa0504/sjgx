package com.platform.partner.consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.platform.common.audit.InMemoryAuditLogRepository;
import com.platform.common.log.JdbcServiceInvokeLogRepository;
import com.platform.common.model.ServiceInvokeLog;
import java.time.Instant;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ConsumerControllerTest {
    @Test
    void registersAndListsConsumer() {
        ConsumerService service = new ConsumerService();
        ConsumerController controller = new ConsumerController(service, new InMemoryAuditLogRepository());

        Consumer consumer = controller.register(new ConsumerController.RegisterConsumerRequest(
                "c1", "consumer-a", "risk", "core", "L2")).data();
        assertEquals("c1", consumer.consumerCode());

        assertTrue(controller.list(null, null, null).data().stream().anyMatch(c -> c.id() == consumer.id()));
    }

    @Test
    void logsEndpointReturnsInvokeLogsFromFactTable() throws Exception {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
        createTables(jdbcTemplate);
        ConsumerService service = new ConsumerService(jdbcTemplate);
        Consumer consumer = service.register("c-log", "consumer-log", "risk", "core", "L2");
        new JdbcServiceInvokeLogRepository(jdbcTemplate).save(new ServiceInvokeLog(
                "trace-consumer", "svc-risk", "c-log", "p1", "ak", "hash",
                200, 12, 64, null, null, Instant.now()));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ConsumerController(service, new InMemoryAuditLogRepository())).build();

        mockMvc.perform(get("/api/v1/consumers/" + consumer.id() + "/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].traceId").value("trace-consumer"))
                .andExpect(jsonPath("$.data.records[0].requestHash").value("hash"));
    }

    private void createTables(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE t_consumer (
                    id BIGINT PRIMARY KEY,
                    consumer_code VARCHAR(64) NOT NULL,
                    name VARCHAR(128) NOT NULL,
                    business_line VARCHAR(64),
                    system_type VARCHAR(64),
                    compliance_level VARCHAR(32),
                    status VARCHAR(32) NOT NULL,
                    created_at TIMESTAMP,
                    updated_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_consumer_event (
                    id BIGINT PRIMARY KEY,
                    consumer_id BIGINT NOT NULL,
                    event VARCHAR(64) NOT NULL,
                    from_status VARCHAR(32),
                    to_status VARCHAR(32),
                    operator VARCHAR(64),
                    created_at TIMESTAMP
                )
                """);
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
        dataSource.setURL("jdbc:h2:mem:consumer-logs;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        return dataSource;
    }
}
