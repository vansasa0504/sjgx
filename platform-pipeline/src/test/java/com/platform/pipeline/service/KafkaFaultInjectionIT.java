package com.platform.pipeline.service;

import com.platform.common.log.JdbcServiceInvokeLogRepository;
import com.platform.common.model.ServiceInvokeLog;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
@EnabledIfEnvironmentVariable(named = "RUN_FAULT_INJECTION_IT", matches = "true")
class KafkaFaultInjectionIT {
    private static final String TOPIC = "fault-it-invoke-log";

    @Container
    static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Test
    void kafkaOutageFallsBackToJdbcAndRecoveryAcceptsKafkaWrites() throws Exception {
        String bootstrapServers = kafka.getBootstrapServers();
        createTopic(bootstrapServers);
        KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate(bootstrapServers);
        AsyncInvokeLogWriter kafkaWriter = new AsyncInvokeLogWriter(kafkaTemplate, TOPIC);
        assertDoesNotThrow(() -> kafkaWriter.write(sample("trace-kafka-up")));

        JdbcServiceInvokeLogRepository repository = new JdbcServiceInvokeLogRepository(jdbcTemplate());
        kafka.stop();
        AsyncInvokeLogWriter fallbackWriter = new AsyncInvokeLogWriter(
                kafkaTemplate(bootstrapServers), TOPIC, repository);
        fallbackWriter.write(sample("trace-kafka-down"));
        assertEquals(1, repository.findByRange(
                Instant.parse("2026-06-29T23:59:59Z"), Instant.parse("2026-06-30T00:00:01Z"), 1, 10).total());

        kafka.start();
        createTopic(kafka.getBootstrapServers());
        AsyncInvokeLogWriter recoveredWriter = new AsyncInvokeLogWriter(
                kafkaTemplate(kafka.getBootstrapServers()), TOPIC);
        assertDoesNotThrow(() -> recoveredWriter.write(sample("trace-kafka-recovered")));
    }

    private void createTopic(String bootstrapServers) throws Exception {
        Map<String, Object> config = Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        try (AdminClient adminClient = AdminClient.create(config)) {
            adminClient.createTopics(List.of(new NewTopic(TOPIC, 1, (short) 1))).all().get();
        } catch (Exception ex) {
            if (ex.getCause() == null || !ex.getCause().getClass().getName().contains("TopicExistsException")) {
                throw ex;
            }
        }
    }

    private KafkaTemplate<String, String> kafkaTemplate(String bootstrapServers) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, "1000");
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "1000");
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "1500");
        config.put(ProducerConfig.RETRIES_CONFIG, "0");
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config));
    }

    private ServiceInvokeLog sample(String traceId) {
        return new ServiceInvokeLog(traceId, "svc-fault", "consumer-fault", "partner-fault", "ak", "hash",
                200, 12, 128, null, null, Instant.parse("2026-06-30T00:00:00Z"));
    }

    private JdbcTemplate jdbcTemplate() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
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

    private JdbcDataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:kafka-fault-it;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        return dataSource;
    }
}
