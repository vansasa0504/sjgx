package com.platform.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.platform.billing.bill.Bill;
import com.platform.billing.bill.BillGenerator;
import com.platform.billing.bill.BillService;
import com.platform.billing.bill.BillStateMachine;
import com.platform.billing.bill.InMemoryBillItemRepository;
import com.platform.billing.bill.InMemoryBillRepository;
import com.platform.billing.dashboard.DashboardService;
import com.platform.billing.finance.MockFinanceSystemAdapter;
import com.platform.billing.job.BillGeneratorJobHandler;
import com.platform.billing.job.StatsAggregatorJobHandler;
import com.platform.billing.model.BillPeriod;
import com.platform.billing.model.BillStatus;
import com.platform.billing.model.BillType;
import com.platform.billing.model.BillingModel;
import com.platform.billing.model.TargetType;
import com.platform.billing.regulatory.MockRegulatoryReportingAdapter;
import com.platform.billing.report.GeneratedReport;
import com.platform.billing.report.ReportGenerator;
import com.platform.billing.report.ReportType;
import com.platform.billing.rule.BillingRule;
import com.platform.billing.rule.BillingRuleEngine;
import com.platform.billing.rule.BillingUsage;
import com.platform.billing.rule.InMemoryBillingRuleRepository;
import com.platform.billing.stats.FixedCacheMetricsProvider;
import com.platform.billing.stats.InMemoryStatsSnapshotRepository;
import com.platform.billing.stats.MetricName;
import com.platform.billing.stats.StatsAggregator;
import com.platform.common.audit.AuditEvent;
import com.platform.common.audit.AuditStatus;
import com.platform.common.audit.InMemoryAuditLogRepository;
import com.platform.common.exception.BusinessException;
import com.platform.common.model.ServiceInvokeLog;
import com.xxl.job.core.handler.annotation.XxlJob;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

class BillingGovernanceTest {
    @Test
    void fiveBillingModelsAndPackageOverlayCalculateAccurately() {
        LocalDate today = LocalDate.now();
        long consumerId = BillGenerator.stableTargetId("c1");
        BillingRuleEngine engine = BillingRuleEngine.defaultEngine(new InMemoryBillingRuleRepository(List.of(
                rule("count", BillingModel.BY_COUNT, consumerId, "0.50", 0),
                rule("volume", BillingModel.BY_VOLUME, consumerId, "0.01", 0),
                rule("interface", BillingModel.BY_INTERFACE, consumerId, "2.00", 0),
                rule("package", BillingModel.BY_PACKAGE, consumerId, "10.00", 3),
                rule("duration", BillingModel.BY_DURATION, consumerId, "0.10", 0)
        )));
        BillingUsage usage = new BillingUsage(TargetType.CONSUMER, consumerId, "svc", "c1", 10, 100, 2, 30, 0);

        BigDecimal amount = engine.calculate(usage, today);

        assertEquals(new BigDecimal("21.5000"), amount);
    }

    @Test
    void billGenerationAndStateFlowWorkAndRejectInvalidTransition() {
        long consumerId = BillGenerator.stableTargetId("c1");
        BillingRuleEngine engine = BillingRuleEngine.defaultEngine(new InMemoryBillingRuleRepository(List.of(
                rule("count", BillingModel.BY_COUNT, consumerId, "1.00", 0)
        )));
        InMemoryBillRepository repository = new InMemoryBillRepository();
        Instant now = Instant.now();
        BillGenerator generator = new BillGenerator(engine, repository, new InMemoryBillItemRepository(), () -> List.of(
                new ServiceInvokeLog("svc", "c1", "p1", 200, 10, 128, now),
                new ServiceInvokeLog("svc", "c1", "p1", 500, 20, 256, now)
        ));
        Bill bill = generator.generate(BillType.EXPENSE, BillPeriod.DAILY, LocalDate.now(), LocalDate.now());
        BillService service = new BillService(repository, new BillStateMachine());

        assertEquals(new BigDecimal("2.0000"), bill.totalAmount());
        assertEquals(bill.totalAmount(), bill.items().stream().map(com.platform.billing.bill.BillItem::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(BillStatus.CONFIRMED, service.confirm(bill.billNo()).status());
        assertEquals(BillStatus.SETTLED, service.settle(bill.billNo()).status());
        assertThrows(BusinessException.class, () -> service.dispute(bill.billNo()));
    }

    @Test
    void statsDashboardAuditAndAdaptersWork() {
        InMemoryStatsSnapshotRepository statsRepository = new InMemoryStatsSnapshotRepository();
        StatsAggregator aggregator = new StatsAggregator(statsRepository, new FixedCacheMetricsProvider(new BigDecimal("0.90")));
        List<ServiceInvokeLog> logs = List.of(
                new ServiceInvokeLog("svc-a", "c1", "p1", 200, 10, 100, Instant.now()),
                new ServiceInvokeLog("svc-a", "c2", "p2", 500, 30, 300, Instant.now())
        );

        var snapshots = aggregator.aggregate(logs, Instant.now());

        assertEquals(new BigDecimal("2"), snapshots.stream().filter(s -> s.metricName() == MetricName.INVOKE_COUNT).findFirst().orElseThrow().metricValue());
        assertEquals(new BigDecimal("0.5000"), snapshots.stream().filter(s -> s.metricName() == MetricName.SUCCESS_RATE).findFirst().orElseThrow().metricValue());
        assertEquals(new BigDecimal("400"), snapshots.stream().filter(s -> s.metricName() == MetricName.TRANSFER_BYTES).findFirst().orElseThrow().metricValue());
        assertEquals(2, new DashboardService().summarize(snapshots, List.of()).invokeCount());

        InMemoryAuditLogRepository auditRepository = new InMemoryAuditLogRepository();
        AuditEvent event = auditRepository.append(new AuditEvent(null, "t1", "STAT", "USER", "u1", "DASHBOARD", "d1", "view", "ok", "", "", AuditStatus.SUCCESS, Instant.now()));
        assertEquals(1, auditRepository.findByTraceId(event.traceId()).size());
        assertThrows(UnsupportedOperationException.class, () -> auditRepository.delete(event.traceId()));

        Bill mockBill = new Bill(1L, "B-1", BillType.EXPENSE, BillPeriod.DAILY, LocalDate.now(), LocalDate.now(), BigDecimal.ONE, BillStatus.GENERATED, Instant.now(), Instant.now());
        assertTrue(new MockFinanceSystemAdapter().sync(mockBill).success());
    }

    @Test
    void reportCanBeGeneratedAndSubmitted(@TempDir Path tempDir) throws Exception {
        GeneratedReport report = new ReportGenerator().generate(ReportType.COMPLIANCE, List.of("source-ok"), tempDir);

        assertTrue(Files.exists(report.path()));
        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(report.path()))) {
            assertEquals("COMPLIANCE", workbook.getSheetAt(0).getSheetName());
            assertEquals("report_type", workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
        }
        assertTrue(new MockRegulatoryReportingAdapter().submit(report).success());
    }

    @Test
    void billNoIsStableAndSettlementGroupsByPartner() {
        LocalDate day = LocalDate.now();
        BillingRuleEngine engine = BillingRuleEngine.defaultEngine(new InMemoryBillingRuleRepository(List.of(
                new BillingRule(null, "settle-p1", "settle-p1", BillingModel.BY_COUNT, TargetType.PARTNER,
                        BillGenerator.stableTargetId("p1"), new BigDecimal("2.00"), "CNY", day.minusDays(1), day.plusDays(1), "ACTIVE", 0),
                new BillingRule(null, "settle-p2", "settle-p2", BillingModel.BY_COUNT, TargetType.PARTNER,
                        BillGenerator.stableTargetId("p2"), new BigDecimal("3.00"), "CNY", day.minusDays(1), day.plusDays(1), "ACTIVE", 0)
        )));
        InMemoryBillRepository repository = new InMemoryBillRepository();
        List<ServiceInvokeLog> logs = List.of(
                new ServiceInvokeLog("svc-a", "c1", "p1", 200, 10, 20, Instant.now()),
                new ServiceInvokeLog("svc-b", "c2", "p2", 200, 10, 30, Instant.now()),
                new ServiceInvokeLog("svc-c", "c3", "p2", 200, 10, 40, Instant.now())
        );
        BillGenerator generator = new BillGenerator(engine, repository, new InMemoryBillItemRepository(), () -> logs);

        Bill first = generator.generate(BillType.SETTLEMENT, BillPeriod.DAILY, day, day);
        Bill second = generator.generate(BillType.SETTLEMENT, BillPeriod.DAILY, day, day);

        assertEquals(first.billNo(), second.billNo());
        assertEquals(new BigDecimal("8.0000"), first.totalAmount());
    }

    @Test
    void v008AndV009MigrationsCreateGovernanceTablesAndPerfColumns() throws Exception {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
        String sql = Files.readString(Path.of("..", "db", "migration", "V005__data_service.sql")) + ";" + System.lineSeparator()
                + Files.readString(Path.of("..", "db", "migration", "V008__governance.sql")) + ";" + System.lineSeparator()
                + Files.readString(Path.of("..", "db", "migration", "V009__perf_and_compat.sql")) + ";" + System.lineSeparator()
                + Files.readString(Path.of("..", "db", "migration", "V013__service_invoke_log_fact_source.sql")) + ";" + System.lineSeparator()
                + Files.readString(Path.of("..", "db", "migration", "V014__bill_item.sql"));
        for (String statement : sql.split(";")) {
            if (!statement.isBlank() && !statement.trim().startsWith("--")) {
                jdbcTemplate.execute(statement);
            }
        }

        assertEquals(7, jdbcTemplate.queryForList("""
                SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_NAME IN ('T_DATA_SERVICE', 'T_SERVICE_INVOKE_LOG', 'T_BILLING_RULE', 'T_BILL', 'T_BILL_ITEM', 'T_STATS_SNAPSHOT', 'T_AUDIT_LOG')
                """).size());
        assertEquals(1, jdbcTemplate.queryForList("""
                SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = 'T_SERVICE_INVOKE_LOG' AND COLUMN_NAME = 'RESPONSE_SIZE'
                """).size());
        assertEquals(1, jdbcTemplate.queryForList("""
                SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = 'T_SERVICE_INVOKE_LOG' AND COLUMN_NAME = 'TRACE_ID'
                """).size());
        assertEquals(1, jdbcTemplate.queryForList("""
                SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = 'T_SERVICE_INVOKE_LOG' AND COLUMN_NAME = 'REQUEST_HASH'
                """).size());
    }

    @Test
    void xxlJobHandlersExposeSchedulerAnnotations() throws Exception {
        assertEquals("billGenerate", BillGeneratorJobHandler.class.getMethod("billGenerate")
                .getAnnotation(XxlJob.class).value());
        assertEquals("statsAggregate", StatsAggregatorJobHandler.class.getMethod("statsAggregate")
                .getAnnotation(XxlJob.class).value());
    }

    private BillingRule rule(String code, BillingModel model, Long targetId, String unitPrice, long allowance) {
        return new BillingRule(null, code, code, model, TargetType.CONSUMER, targetId, new BigDecimal(unitPrice), "CNY",
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), "ACTIVE", allowance);
    }

    private JdbcDataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:billing;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        return dataSource;
    }
}


