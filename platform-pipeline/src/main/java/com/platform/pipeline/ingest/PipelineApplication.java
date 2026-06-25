package com.platform.pipeline.ingest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;

@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = "com.platform")
public class PipelineApplication {
    public static void main(String[] args) {
        SpringApplication.run(PipelineApplication.class, args);
    }

    @Bean
    IngestService ingestService() {
        return new IngestService(new HttpAdapter(), new JsonConverter(), new RawDataRepository());
    }
}
