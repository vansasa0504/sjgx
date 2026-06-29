package com.platform.pipeline;

import com.platform.common.audit.AuditEvent;
import com.platform.common.audit.AuditChainVerification;
import com.platform.common.audit.AuditLogRepository;
import com.platform.common.audit.AuditStatus;
import com.platform.pipeline.ingest.IngestService;
import com.platform.pipeline.ingest.IngestTask;
import com.platform.pipeline.service.ApiCredentialRepository;
import com.platform.pipeline.service.DataServiceEvent;
import com.platform.pipeline.service.DataServiceManager;
import com.platform.pipeline.storage.tier.ColdStorageStore;
import com.platform.pipeline.storage.tier.StorageTier;
import com.platform.pipeline.storage.tier.TieredRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = com.platform.pipeline.ingest.PipelineApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@EnabledIfEnvironmentVariable(named = "RUN_REAL_DEPS_IT", matches = "true")
class RealDependenciesIT {
    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("sjgx")
            .withUsername("sjgx")
            .withPassword("sjgx");

    @Container
    static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Container
    static final RabbitMQContainer rabbit = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3-management"));

    @Container
    static final GenericContainer<?> minio = new GenericContainer<>(DockerImageName.parse("minio/minio:latest"))
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
            .withCommand("server /data")
            .withExposedPorts(9000);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "filesystem:" + migrationPath());
        registry.add("spring.cloud.nacos.discovery.enabled", () -> "false");
        registry.add("pipeline.invoke-log.kafka-enabled", () -> "true");
        registry.add("pipeline.invoke-log.topic", () -> "service-invoke-logs-it");
        registry.add("pipeline.invoke-log.group-id", () -> "platform-pipeline-it-" + System.nanoTime());
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbit::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbit::getAdminPassword);
        registry.add("storage.minio.enabled", () -> "true");
        registry.add("storage.minio.endpoint", () -> "http://" + minio.getHost() + ":" + minio.getMappedPort(9000));
        registry.add("storage.minio.access-key", () -> "minioadmin");
        registry.add("storage.minio.secret-key", () -> "minioadmin");
        registry.add("storage.minio.bucket", () -> "sjgx-it-cold-storage");
    }

    private static String migrationPath() {
        return Path.of("..", "db", "migration").toAbsolutePath().normalize().toString();
    }

    @Autowired DataServiceManager dataServiceManager;
    @Autowired IngestService ingestService;
    @Autowired ColdStorageStore coldStorageStore;
    @Autowired RabbitTemplate rabbitTemplate;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired AuditLogRepository auditLogRepository;
    @LocalServerPort int port;

    @Test
    void realDependenciesBackMainPipelineGates() throws Exception {
        dataServiceManager.register("svc-real", "真实依赖服务", "route-real");
        dataServiceManager.apply("svc-real", DataServiceEvent.DEFINE);
        dataServiceManager.apply("svc-real", DataServiceEvent.TEST);
        dataServiceManager.apply("svc-real", DataServiceEvent.PUBLISH);
        ApiCredentialRepository.CreatedCredential credential = dataServiceManager.createCredential("svc-real", "consumer-real");
        long timestamp = Instant.now().getEpochSecond();
        String body = "{\"amount\":1}";
        String signature = dataServiceManager.signatureUtil()
                .sign(credential.apiKey(), credential.secret(), timestamp, "nonce-real-it", body);

        assertEquals("{\"status\":\"ok\"}", dataServiceManager.invoke("svc-real", null, credential.apiKey(),
                timestamp, "nonce-real-it", body, signature, "trace-real-it"));
        awaitInvokeLog();

        String queue = "p0.real.deps.queue";
        rabbitTemplate.execute(channel -> {
            channel.queueDeclare(queue, false, false, true, null);
            return null;
        });
        rabbitTemplate.convertAndSend(queue, "[{\"id\":1,\"name\":\"mq\"}]");
        IngestTask mqTask = ingestService.createTask(1L, URI.create("mq:" + queue), "REALTIME", null, null, null);
        assertEquals("MQ", mqTask.protocol());
        assertEquals(1, ingestService.run(mqTask.id()).size());

        coldStorageStore.write(new TieredRecord("cold-real-it", "{\"ok\":true}", StorageTier.COLD));
        List<TieredRecord> coldRecords = coldStorageStore.readAll();
        assertTrue(coldRecords.stream().anyMatch(record -> record.key().equals("cold-real-it")));

        auditLogRepository.append(new AuditEvent(null, "trace-real-it", "REAL_DEPS_IT", "SYSTEM", "it",
                "PIPELINE", "verify", "verify", "real deps", "", "", AuditStatus.SUCCESS, Instant.now()));
        AuditChainVerification verification = auditLogRepository.verify();
        assertTrue(verification.intact(), verification.toString());
        assertFalse(auditLogRepository.findByTraceId("trace-real-it").isEmpty());
    }

    private void awaitInvokeLog() throws InterruptedException {
        for (int i = 0; i < 40; i++) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_service_invoke_log WHERE trace_id = ?",
                    Integer.class,
                    "trace-real-it");
            if (count != null && count > 0) {
                return;
            }
            Thread.sleep(250);
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_service_invoke_log WHERE trace_id = ?",
                Integer.class,
                "trace-real-it");
        assertTrue(count != null && count > 0, "invoke log should be consumed from Kafka into JDBC");
    }
}
