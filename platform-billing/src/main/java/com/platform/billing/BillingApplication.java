package com.platform.billing;

import com.platform.billing.bill.BillGenerator;
import com.platform.billing.bill.BillItemRepository;
import com.platform.billing.bill.BillRepository;
import com.platform.billing.bill.BillService;
import com.platform.billing.bill.BillStateMachine;
import com.platform.billing.bill.InMemoryBillItemRepository;
import com.platform.billing.bill.InMemoryBillRepository;
import com.platform.billing.bill.JdbcBillItemRepository;
import com.platform.billing.bill.JdbcBillRepository;
import com.platform.billing.dashboard.DashboardService;
import com.platform.billing.regulatory.MockRegulatoryReportingAdapter;
import com.platform.billing.regulatory.RegulatoryReportingAdapter;
import com.platform.billing.report.ReportGenerator;
import com.platform.billing.report.InMemoryRegulatoryReportRepository;
import com.platform.billing.report.JdbcRegulatoryReportRepository;
import com.platform.billing.report.RegulatoryReportRepository;
import com.platform.billing.report.RegulatoryReportService;
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
import com.platform.common.log.JdbcServiceInvokeLogRepository;
import java.math.BigDecimal;
import java.util.List;
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
    BillItemRepository billItemRepository(
            @Autowired(required = false) JdbcTemplate jdbcTemplate) {
        return jdbcTemplate != null
                ? new JdbcBillItemRepository(jdbcTemplate)
                : new InMemoryBillItemRepository();
    }

    @Bean
    BillRepository billRepository(
            @Autowired(required = false) JdbcTemplate jdbcTemplate,
            BillItemRepository billItemRepository) {
        return jdbcTemplate != null
                ? new JdbcBillRepository(jdbcTemplate, billItemRepository)
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
    BillGenerator billGenerator(BillingRuleEngine ruleEngine, BillRepository billRepository,
                                BillItemRepository billItemRepository,
                                @Autowired(required = false) JdbcServiceInvokeLogRepository invokeLogRepository) {
        return new BillGenerator(ruleEngine, billRepository, billItemRepository,
                () -> invokeLogRepository == null ? List.of() : invokeLogRepository.findAll());
    }

    @Bean
    JdbcServiceInvokeLogRepository jdbcServiceInvokeLogRepository(
            @Autowired(required = false) JdbcTemplate jdbcTemplate) {
        return jdbcTemplate == null ? null : new JdbcServiceInvokeLogRepository(jdbcTemplate);
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
    RegulatoryReportingAdapter regulatoryReportingAdapter() {
        return new MockRegulatoryReportingAdapter();
    }

    @Bean
    RegulatoryReportRepository regulatoryReportRepository(
            @Autowired(required = false) JdbcTemplate jdbcTemplate) {
        return jdbcTemplate != null
                ? new JdbcRegulatoryReportRepository(jdbcTemplate)
                : new InMemoryRegulatoryReportRepository();
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

    @Bean
    RegulatoryReportService regulatoryReportService(
            RegulatoryReportRepository regulatoryReportRepository,
            RegulatoryReportingAdapter regulatoryReportingAdapter,
            AuditLogRepository auditLogRepository,
            @Autowired(required = false) JdbcServiceInvokeLogRepository invokeLogRepository) {
        return new RegulatoryReportService(
                () -> invokeLogRepository == null ? List.of() : invokeLogRepository.findAll(),
                regulatoryReportRepository, regulatoryReportingAdapter, auditLogRepository);
    }

    @Bean
    com.platform.billing.job.BillGeneratorJobHandler billGeneratorJobHandler(
            BillGenerator billGenerator) {
        return new com.platform.billing.job.BillGeneratorJobHandler(billGenerator);
    }

    @Bean
    com.platform.billing.job.StatsAggregatorJobHandler statsAggregatorJobHandler(
            StatsAggregator statsAggregator,
            @Autowired(required = false) JdbcServiceInvokeLogRepository invokeLogRepository) {
        return new com.platform.billing.job.StatsAggregatorJobHandler(statsAggregator,
                () -> invokeLogRepository == null ? List.of() : invokeLogRepository.findAll());
    }
}
