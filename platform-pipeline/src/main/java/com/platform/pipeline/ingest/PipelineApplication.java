package com.platform.pipeline.ingest;

import com.platform.common.audit.AuditLogRepository;
import com.platform.common.audit.InMemoryAuditLogRepository;
import com.platform.common.audit.JdbcAuditLogRepository;
import com.platform.pipeline.catalog.CatalogApplicationRepository;
import com.platform.pipeline.catalog.InMemoryCatalogApplicationRepository;
import com.platform.pipeline.catalog.JdbcCatalogApplicationRepository;
import com.platform.pipeline.catalog.CatalogService;
import com.platform.pipeline.ingest.adapter.MqAdapter;
import com.platform.pipeline.service.ApiCredentialRepository;
import com.platform.pipeline.service.AsyncInvokeLogWriter;
import com.platform.pipeline.service.DataServiceManager;
import com.platform.pipeline.storage.tier.ColdStorageStore;
import com.platform.pipeline.storage.tier.LocalColdStorageStore;
import com.platform.pipeline.storage.tier.MinioColdStorageStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

@EnableDiscoveryClient
@EnableKafka
@SpringBootApplication(scanBasePackages = {"com.platform.pipeline", "com.platform.common"})
public class PipelineApplication {
    public static void main(String[] args) {
        SpringApplication.run(PipelineApplication.class, args);
    }

    @Bean
    HttpAdapter httpAdapter() {
        return new HttpAdapter();
    }

    @Bean
    IngestService ingestService(HttpAdapter httpAdapter,
                                @Autowired(required = false) List<ProtocolAdapter> adapters,
                                @Autowired(required = false) JdbcTemplate jdbcTemplate) {
        return new IngestService(adapters == null ? List.of(httpAdapter) : adapters, httpAdapter,
                new JsonConverter(), new RawDataRepository(), IngestQualityGuard.disabled(), jdbcTemplate);
    }

    @Bean
    ProtocolAdapter mqAdapter(ObjectProvider<RabbitTemplate> rabbitTemplateProvider) {
        RabbitTemplate rabbitTemplate = rabbitTemplateProvider.getIfAvailable();
        if (rabbitTemplate != null) {
            return new MqAdapter(rabbitTemplate);
        }
        return new ProtocolAdapter() {
            @Override
            public String protocol() {
                return "MQ";
            }

            @Override
            public String fetch(java.net.URI endpoint) {
                throw new IllegalStateException("RabbitTemplate is not configured for MQ ingest");
            }
        };
    }

    @Bean
    AsyncInvokeLogWriter asyncInvokeLogWriter(
            @Autowired(required = false) JdbcTemplate jdbcTemplate,
            ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider,
            ObjectMapper objectMapper,
            @Value("${pipeline.invoke-log.kafka-enabled:false}") boolean kafkaEnabled,
            @Value("${pipeline.invoke-log.topic:service-invoke-logs}") String topic) {
        var repository = jdbcTemplate == null ? null : new com.platform.common.log.JdbcServiceInvokeLogRepository(jdbcTemplate);
        KafkaTemplate<String, String> kafkaTemplate = kafkaEnabled ? kafkaTemplateProvider.getIfAvailable() : null;
        return new AsyncInvokeLogWriter(kafkaTemplate, topic, repository, objectMapper);
    }

    @Bean
    DataServiceManager dataServiceManager(@Autowired(required = false) JdbcTemplate jdbcTemplate,
                                          AsyncInvokeLogWriter asyncInvokeLogWriter) {
        return new DataServiceManager(new ApiCredentialRepository(jdbcTemplate), jdbcTemplate, asyncInvokeLogWriter);
    }

    @Bean
    @ConditionalOnProperty(prefix = "storage.minio", name = "enabled", havingValue = "true")
    MinioClient minioClient(@Value("${storage.minio.endpoint}") String endpoint,
                            @Value("${storage.minio.access-key}") String accessKey,
                            @Value("${storage.minio.secret-key}") String secretKey) {
        return MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
    }

    @Bean
    ColdStorageStore coldStorageStore(ObjectProvider<MinioClient> minioClientProvider,
                                      @Value("${storage.minio.bucket:sjgx-cold-storage}") String bucket) {
        MinioClient minioClient = minioClientProvider.getIfAvailable();
        return minioClient == null ? new LocalColdStorageStore() : new MinioColdStorageStore(minioClient, bucket);
    }

    @Bean
    CatalogService catalogService(@Autowired(required = false) JdbcTemplate jdbcTemplate) {
        return new CatalogService(jdbcTemplate);
    }

    @Bean
    CatalogApplicationRepository catalogApplicationRepository(@Autowired(required = false) JdbcTemplate jdbcTemplate) {
        return jdbcTemplate == null
                ? new InMemoryCatalogApplicationRepository()
                : new JdbcCatalogApplicationRepository(jdbcTemplate);
    }

    @Bean
    AuditLogRepository auditLogRepository(@Autowired(required = false) JdbcTemplate jdbcTemplate) {
        return jdbcTemplate == null ? new InMemoryAuditLogRepository() : new JdbcAuditLogRepository(jdbcTemplate);
    }
}
