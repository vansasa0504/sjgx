package com.platform.pipeline.ingest;

import com.platform.pipeline.catalog.CatalogService;
import com.platform.pipeline.service.DataServiceManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;

@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = {"com.platform.pipeline", "com.platform.common"})
public class PipelineApplication {
    public static void main(String[] args) {
        SpringApplication.run(PipelineApplication.class, args);
    }

    @Bean
    IngestService ingestService() {
        return new IngestService(new HttpAdapter(), new JsonConverter(), new RawDataRepository());
    }

    @Bean
    DataServiceManager dataServiceManager() {
        return new DataServiceManager();
    }

    @Bean
    CatalogService catalogService() {
        return new CatalogService();
    }
}
