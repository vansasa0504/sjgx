package com.platform.billing;

import com.platform.billing.bill.BillGenerator;
import com.platform.billing.bill.BillRepository;
import com.platform.billing.bill.BillService;
import com.platform.billing.bill.BillStateMachine;
import com.platform.billing.bill.InMemoryBillRepository;
import com.platform.billing.bill.JdbcBillRepository;
import com.platform.billing.dashboard.DashboardService;
import com.platform.billing.report.ReportGenerator;
import com.platform.billing.rule.BillingRuleEngine;
import com.platform.billing.rule.BillingRuleRepository;
import com.platform.billing.rule.InMemoryBillingRuleRepository;
import com.platform.billing.rule.JdbcBillingRuleRepository;
import com.platform.billing.stats.AuditTraceService;
import com.platform.billing.stats.CacheMetricsProvider;
import com.platform.billing.stats.FixedCacheMetricsProvider;
import com.platform.billing.stats.InMemoryStatsSnapshotRepository;
import com.platform.billing.stats.JdbcStatsSnapshotRepository;
import com.platform.billing.stats.StatsAggregator;
import com.platform.billing.stats.StatsSnapshotRepository;
import com.platform.common.audit.AuditLogRepository;
import com.platform.common.audit.InMemoryAuditLogRepository;
import java.math.BigDecimal;
import org.springframework.boot.SpringApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootApplication(scanBasePackages = {"com.platform.billing", "com.platform.common"})
public class BillingApplication {
    public static void main(String[] args) {
        SpringApplication.run(BillingApplication.class, args);
    }

    @Bean
    BillingRuleRepository billingRuleRepository(
            @Autowired(required = false) JdbcTemplate jdbcTemplate) {
        return jdbcTemplate != null
                ? new JdbcBillingRuleRepository(jdbcTemplate)
                : new InMemoryBillingRuleRepository();
    }

    @Bean
    BillingRuleEngine billingRuleEngine(BillingRuleRepository repository) {
        return BillingRuleEngine.defaultEngine(repository);
    }

    @Bean
    BillRepository billRepository(
            @Autowired(required = false) JdbcTemplate jdbcTemplate) {
        return jdbcTemplate != null
                ? new JdbcBillRepository(jdbcTemplate)
                : new InMemoryBillRepository();
    }

    @Bean
    BillStateMachine billStateMachine() {
        return new BillStateMachine();
    }

    @Bean
    BillService billService(BillRepository billRepository, BillStateMachine stateMachine) {
        return new BillService(billRepository, stateMachine);
    }

    @Bean
    BillGenerator billGenerator(BillingRuleEngine ruleEngine, BillRepository billRepository) {
        return new BillGenerator(ruleEngine, billRepository);
    }

    @Bean
    StatsSnapshotRepository statsSnapshotRepository(
            @Autowired(required = false) JdbcTemplate jdbcTemplate) {
        return jdbcTemplate != null
                ? new JdbcStatsSnapshotRepository(jdbcTemplate)
                : new InMemoryStatsSnapshotRepository();
    }

    @Bean
    CacheMetricsProvider cacheMetricsProvider() {
        return new FixedCacheMetricsProvider(new BigDecimal("0.90"));
    }

    @Bean
    StatsAggregator statsAggregator(StatsSnapshotRepository repository, CacheMetricsProvider cacheMetricsProvider) {
        return new StatsAggregator(repository, cacheMetricsProvider);
    }

    @Bean
    DashboardService dashboardService() {
        return new DashboardService();
    }

    @Bean
    ReportGenerator reportGenerator() {
        return new ReportGenerator();
    }

    @Bean
    AuditLogRepository auditLogRepository(
            @Autowired(required = false) JdbcTemplate jdbcTemplate) {
        return jdbcTemplate != null
                ? new com.platform.common.audit.JdbcAuditLogRepository(jdbcTemplate)
                : new InMemoryAuditLogRepository();
    }

    @Bean
    AuditTraceService auditTraceService(AuditLogRepository auditLogRepository) {
        return new AuditTraceService(auditLogRepository);
    }
}