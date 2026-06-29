package com.platform.pipeline.ingest;

import com.platform.common.audit.AuditLogRepository;
import com.platform.common.audit.InMemoryAuditLogRepository;
import com.platform.common.audit.JdbcAuditLogRepository;
import com.platform.pipeline.catalog.CatalogApplicationRepository;
import com.platform.pipeline.catalog.CatalogGovernanceService;
import com.platform.pipeline.catalog.CatalogLineageRepository;
import com.platform.pipeline.catalog.CatalogQualitySummaryRepository;
import com.platform.pipeline.catalog.InMemoryCatalogApplicationRepository;
import com.platform.pipeline.catalog.InMemoryCatalogLineageRepository;
import com.platform.pipeline.catalog.InMemoryCatalogQualitySummaryRepository;
import com.platform.pipeline.catalog.JdbcCatalogApplicationRepository;
import com.platform.pipeline.catalog.JdbcCatalogLineageRepository;
import com.platform.pipeline.catalog.JdbcCatalogQualitySummaryRepository;
import com.platform.pipeline.catalog.CatalogService;
import com.platform.pipeline.ingest.adapter.MqAdapter;
import com.platform.pipeline.ingest.adapter.ApiGatewayAdapter;
import com.platform.pipeline.ingest.adapter.DbAdapter;
import com.platform.pipeline.ingest.adapter.FtpAdapter;
import com.platform.pipeline.ingest.adapter.KafkaAdapter;
import com.platform.pipeline.ingest.adapter.SftpAdapter;
import com.platform.pipeline.ingest.adapter.WebServiceAdapter;
import com.platform.pipeline.ingest.sync.InMemoryOffsetStore;
import com.platform.pipeline.ingest.sync.JdbcOffsetStore;
import com.platform.pipeline.ingest.sync.OffsetStore;
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
import java.util.Map;

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
    ProtocolAdapter webServiceAdapter() {
        return new WebServiceAdapter();
    }

    @Bean
    ProtocolAdapter apiGatewayAdapter() {
        return new ApiGatewayAdapter();
    }

    @Bean
    ProtocolAdapter ftpAdapter() {
        return new FtpAdapter();
    }

    @Bean
    ProtocolAdapter sftpAdapter() {
        return new SftpAdapter();
    }

    @Bean
    ProtocolAdapter kafkaAdapter(@Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        return new KafkaAdapter(bootstrapServers);
    }

    @Bean
    ProtocolAdapter dbAdapter() {
        return new DbAdapter(Map.of());
    }

    @Bean
    OffsetStore offsetStore(@Autowired(required = false) JdbcTemplate jdbcTemplate) {
        return jdbcTemplate == null ? new InMemoryOffsetStore() : new JdbcOffsetStore(jdbcTemplate);
    }

    @Bean
    IngestService ingestService(HttpAdapter httpAdapter,
                                @Autowired(required = false) List<ProtocolAdapter> adapters,
                                @Autowired(required = false) JdbcTemplate jdbcTemplate,
                                OffsetStore offsetStore) {
        return new IngestService(adapters == null ? List.of(httpAdapter) : adapters, httpAdapter,
                new JsonConverter(), new RawDataRepository(), IngestQualityGuard.disabled(), jdbcTemplate, offsetStore);
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
    CatalogService catalogService(@Autowired(required = false) JdbcTemplate jdbcTemplate,
                                  CatalogLineageRepository lineageRepository,
                                  CatalogQualitySummaryRepository qualitySummaryRepository) {
        return new CatalogService(jdbcTemplate, lineageRepository, qualitySummaryRepository);
    }

    @Bean
    CatalogApplicationRepository catalogApplicationRepository(@Autowired(required = false) JdbcTemplate jdbcTemplate) {
        return jdbcTemplate == null
                ? new InMemoryCatalogApplicationRepository()
                : new JdbcCatalogApplicationRepository(jdbcTemplate);
    }

    @Bean
    CatalogLineageRepository catalogLineageRepository(@Autowired(required = false) JdbcTemplate jdbcTemplate) {
        return jdbcTemplate == null
                ? new InMemoryCatalogLineageRepository()
                : new JdbcCatalogLineageRepository(jdbcTemplate);
    }

    @Bean
    CatalogQualitySummaryRepository catalogQualitySummaryRepository(@Autowired(required = false) JdbcTemplate jdbcTemplate) {
        return jdbcTemplate == null
                ? new InMemoryCatalogQualitySummaryRepository()
                : new JdbcCatalogQualitySummaryRepository(jdbcTemplate);
    }

    @Bean
    CatalogGovernanceService catalogGovernanceService(CatalogService catalogService,
                                                      CatalogLineageRepository lineageRepository,
                                                      CatalogQualitySummaryRepository qualitySummaryRepository,
                                                      CatalogApplicationRepository applicationRepository,
                                                      @Autowired(required = false) JdbcTemplate jdbcTemplate) {
        return new CatalogGovernanceService(catalogService, lineageRepository, qualitySummaryRepository,
                applicationRepository, jdbcTemplate);
    }

    @Bean
    AuditLogRepository auditLogRepository(@Autowired(required = false) JdbcTemplate jdbcTemplate) {
        return jdbcTemplate == null ? new InMemoryAuditLogRepository() : new JdbcAuditLogRepository(jdbcTemplate);
    }
}
