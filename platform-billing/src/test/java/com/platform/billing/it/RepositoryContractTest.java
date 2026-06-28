package com.platform.billing.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.platform.billing.bill.Bill;
import com.platform.billing.bill.BillItem;
import com.platform.billing.bill.JdbcBillItemRepository;
import com.platform.billing.bill.JdbcBillRepository;
import com.platform.billing.bill.InMemoryBillItemRepository;
import com.platform.billing.bill.InMemoryBillRepository;
import com.platform.billing.model.BillPeriod;
import com.platform.billing.model.BillStatus;
import com.platform.billing.model.BillType;
import com.platform.billing.rule.BillingRule;
import com.platform.billing.rule.JdbcBillingRuleRepository;
import com.platform.billing.rule.InMemoryBillingRuleRepository;
import com.platform.billing.model.BillingModel;
import com.platform.billing.model.TargetType;
import com.platform.billing.stats.JdbcStatsSnapshotRepository;
import com.platform.billing.stats.InMemoryStatsSnapshotRepository;
import com.platform.billing.stats.MetricName;
import com.platform.billing.stats.StatsDimension;
import com.platform.billing.stats.StatsSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class RepositoryContractTest {

    @Test
    void jdbcAndMemoryBillRepositoriesBehaveConsistently() {
        JdbcTemplate jdbc = migrate("contract_bill");
        JdbcBillItemRepository jdbcItemRepo = new JdbcBillItemRepository(jdbc);
        InMemoryBillItemRepository memItemRepo = new InMemoryBillItemRepository();
        JdbcBillRepository jdbcRepo = new JdbcBillRepository(jdbc, jdbcItemRepo);
        InMemoryBillRepository memRepo = new InMemoryBillRepository();

        Bill bill = sampleBill("BILL-001");

        Bill jdbcBill = jdbcRepo.save(bill);
        Bill memBill = memRepo.save(bill);
        List<BillItem> jdbcItems = jdbcItemRepo.saveAll(jdbcBill.billNo(), jdbcBill.id(), List.of(sampleItem(jdbcBill)));
        List<BillItem> memItems = memItemRepo.saveAll(memBill.billNo(), memBill.id(), List.of(sampleItem(memBill)));
        jdbcRepo.save(new Bill(jdbcBill.id(), jdbcBill.billNo(), jdbcBill.billType(), jdbcBill.billPeriod(),
                jdbcBill.periodStart(), jdbcBill.periodEnd(), jdbcBill.totalAmount(), jdbcBill.status(),
                jdbcBill.createdAt(), jdbcBill.updatedAt(), jdbcItems));
        memRepo.save(new Bill(memBill.id(), memBill.billNo(), memBill.billType(), memBill.billPeriod(),
                memBill.periodStart(), memBill.periodEnd(), memBill.totalAmount(), memBill.status(),
                memBill.createdAt(), memBill.updatedAt(), memItems));

        var jdbcFound = jdbcRepo.findByBillNo("BILL-001").orElseThrow();
        var memFound = memRepo.findByBillNo("BILL-001").orElseThrow();

        assertEquals(memFound.billNo(), jdbcFound.billNo());
        assertEquals(memFound.totalAmount(), jdbcFound.totalAmount());
        assertEquals(memFound.status(), jdbcFound.status());
        assertEquals(memFound.items().size(), jdbcFound.items().size());
        assertEquals(memFound.items().get(0).amount(), jdbcFound.items().get(0).amount());

        assertEquals(1, jdbcRepo.findAll().size());
        assertEquals(1, memRepo.findAll().size());
    }

    @Test
    void jdbcAndMemoryBillingRuleRepositoriesBehaveConsistently() {
        JdbcTemplate jdbc = migrate("contract_rule");
        JdbcBillingRuleRepository jdbcRepo = new JdbcBillingRuleRepository(jdbc);
        InMemoryBillingRuleRepository memRepo = new InMemoryBillingRuleRepository();

        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 12, 31);
        BillingRule rule = new BillingRule(null, "RULE-01", "Test Rule", BillingModel.BY_COUNT,
                TargetType.CONSUMER, 1L, new BigDecimal("1.50"), "CNY", from, to, "ACTIVE", 0);

        jdbcRepo.save(rule);
        memRepo.save(rule);

        List<BillingRule> jdbcActive = jdbcRepo.activeRules(LocalDate.of(2026, 6, 1));
        List<BillingRule> memActive = memRepo.activeRules(LocalDate.of(2026, 6, 1));

        assertEquals(memActive.size(), jdbcActive.size());
        assertEquals("RULE-01", jdbcActive.get(0).ruleCode());
        assertEquals(0, memActive.get(0).unitPrice().compareTo(jdbcActive.get(0).unitPrice()));

        assertEquals(memRepo.findAll().size(), jdbcRepo.findAll().size());
    }

    @Test
    void jdbcAndMemoryStatsRepositoriesBehaveConsistently() {
        JdbcTemplate jdbc = migrate("contract_stats");
        JdbcStatsSnapshotRepository jdbcRepo = new JdbcStatsSnapshotRepository(jdbc);
        InMemoryStatsSnapshotRepository memRepo = new InMemoryStatsSnapshotRepository();

        StatsSnapshot snapshot = new StatsSnapshot(null, MetricName.INVOKE_COUNT, StatsDimension.SERVICE,
                1L, new BigDecimal("100"), Instant.now());

        jdbcRepo.save(snapshot);
        memRepo.save(snapshot);

        assertEquals(memRepo.findAll().size(), jdbcRepo.findAll().size());
        assertTrue(jdbcRepo.findAll().get(0).metricValue().compareTo(new BigDecimal("100")) == 0);
    }

    private Bill sampleBill(String billNo) {
        return new Bill(null, billNo, BillType.EXPENSE, BillPeriod.DAILY,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1),
                new BigDecimal("99.0000"), BillStatus.GENERATED, Instant.now(), Instant.now());
    }

    private BillItem sampleItem(Bill bill) {
        return new BillItem(null, bill.id(), bill.billNo(), "CONSUMER", "1", 99,
                new BigDecimal("1.000000"), new BigDecimal("99.0000"), "DAILY:2026-06-01:2026-06-01",
                "svc", "c1", null, Instant.now());
    }

    private JdbcTemplate migrate(String name) {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        Flyway.configure().dataSource(ds).locations("filesystem:../db/migration").load().migrate();
        return jdbc;
    }
}
