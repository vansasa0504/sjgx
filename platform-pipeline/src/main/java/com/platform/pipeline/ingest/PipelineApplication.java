package com.platform.pipeline.ingest;

import com.platform.common.audit.AuditLogRepository;
import com.platform.common.audit.InMemoryAuditLogRepository;
import com.platform.common.audit.JdbcAuditLogRepository;
import com.platform.pipeline.catalog.CatalogApplicationRepository;
import com.platform.pipeline.catalog.InMemoryCatalogApplicationRepository;
import com.platform.pipeline.catalog.JdbcCatalogApplicationRepository;
import com.platform.pipeline.catalog.CatalogService;
import com.platform.pipeline.service.ApiCredentialRepository;
import com.platform.pipeline.service.DataServiceManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = {"com.platform.pipeline", "com.platform.common"})
public class PipelineApplication {
    public static void main(String[] args) {
        SpringApplication.run(PipelineApplication.class, args);
    }

    @Bean
    IngestService ingestService(@Autowired(required = false) JdbcTemplate jdbcTemplate) {
        return new IngestService(new HttpAdapter(), new JsonConverter(), new RawDataRepository(), IngestQualityGuard.disabled(), jdbcTemplate);
    }

    @Bean
    DataServiceManager dataServiceManager(@Autowired(required = false) JdbcTemplate jdbcTemplate) {
        return new DataServiceManager(new ApiCredentialRepository(jdbcTemplate), jdbcTemplate);
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
