package com.platform.partner;

import com.platform.common.audit.AuditLogRepository;
import com.platform.common.audit.InMemoryAuditLogRepository;
import com.platform.partner.consumer.ConsumerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = {"com.platform.partner", "com.platform.common"})
public class PartnerApplication {
    public static void main(String[] args) {
        SpringApplication.run(PartnerApplication.class, args);
    }

    @Bean
    PartnerService partnerService(@Autowired(required = false) JdbcTemplate jdbcTemplate) {
        return new PartnerService(System.getenv().getOrDefault("PARTNER_CREDENTIAL_KEY", "change-me-in-env"), jdbcTemplate);
    }

    @Bean
    ConsumerService consumerService(@Autowired(required = false) JdbcTemplate jdbcTemplate) {
        return new ConsumerService(jdbcTemplate);
    }

    @Bean
    AuditLogRepository auditLogRepository() {
        return new InMemoryAuditLogRepository();
    }
}
