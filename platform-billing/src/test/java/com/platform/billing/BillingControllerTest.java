package com.platform.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.platform.billing.bill.BillGenerator;
import com.platform.billing.bill.BillService;
import com.platform.billing.bill.BillStateMachine;
import com.platform.billing.bill.InMemoryBillItemRepository;
import com.platform.billing.bill.InMemoryBillRepository;
import com.platform.billing.model.BillPeriod;
import com.platform.billing.model.BillType;
import com.platform.billing.model.BillingModel;
import com.platform.billing.model.TargetType;
import com.platform.billing.rule.BillingRuleEngine;
import com.platform.billing.rule.InMemoryBillingRuleRepository;
import com.platform.common.log.JdbcServiceInvokeLogRepository;
import com.platform.common.model.ServiceInvokeLog;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class BillingControllerTest {
    @Test
    void createsAndListsRule() {
        var ruleRepository = new InMemoryBillingRuleRepository();
        var billRepository = new InMemoryBillRepository();
        var billItemRepository = new InMemoryBillItemRepository();
        var engine = BillingRuleEngine.defaultEngine(ruleRepository);
        var billGenerator = new BillGenerator(engine, billRepository, billItemRepository, java.util.List::of);
        var billService = new BillService(billRepository, new BillStateMachine());
        BillingController controller = new BillingController(ruleRepository, billGenerator, billRepository, billItemRepository, billService);

        var rule = controller.createRule(new BillingController.CreateRuleRequest(
                "count-c1", "count-c1", BillingModel.BY_COUNT, TargetType.CONSUMER, 1L,
                new BigDecimal("1.00"), "CNY", LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), 0)).data();
        assertEquals("count-c1", rule.ruleCode());
        assertTrue(controller.listRules(null).data().stream().anyMatch(r -> "count-c1".equals(r.ruleCode())));
    }

    @Test
    void generatesBillFromInvokeLogFactTableWhenRepositoryExists() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
        createInvokeLogTable(jdbcTemplate);
        JdbcServiceInvokeLogRepository invokeLogRepository = new JdbcServiceInvokeLogRepository(jdbcTemplate);
        invokeLogRepository.save(new ServiceInvokeLog("trace-bill", "svc-risk", "c-bill", "p1",
                "ak", "hash", 200, 8, 32, null, null, Instant.now()));
        var ruleRepository = new InMemoryBillingRuleRepository();
        ruleRepository.save(new BillingController.CreateRuleRequest(
                "count-c-bill", "count-c-bill", BillingModel.BY_COUNT, TargetType.CONSUMER,
                com.platform.billing.bill.BillGenerator.stableTargetId("c-bill"), new BigDecimal("1.00"),
                "CNY", LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), 0).toRule(null));
        var billRepository = new InMemoryBillRepository();
        var billItemRepository = new InMemoryBillItemRepository();
        var engine = BillingRuleEngine.defaultEngine(ruleRepository);
        var billGenerator = new BillGenerator(engine, billRepository, billItemRepository, invokeLogRepository::findAll);
        var billService = new BillService(billRepository, new BillStateMachine());
        BillingController controller = new BillingController(ruleRepository, billGenerator, billRepository, billItemRepository, billService);

        var bill = controller.generate(new BillingController.GenerateBillRequest(
                BillType.EXPENSE, BillPeriod.DAILY, LocalDate.now(), LocalDate.now())).data();

        assertEquals(new BigDecimal("1.0000"), bill.totalAmount());
        assertEquals(bill.totalAmount(), bill.items().stream().map(com.platform.billing.bill.BillItem::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private void createInvokeLogTable(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_service_invoke_log (
                    id BIGINT PRIMARY KEY,
                    trace_id VARCHAR(64),
                    service_code VARCHAR(64) NOT NULL,
                    consumer_code VARCHAR(64) NOT NULL,
                    partner_code VARCHAR(64),
                    api_key VARCHAR(128),
                    request_hash VARCHAR(128),
                    status_code INT NOT NULL,
                    elapsed_millis BIGINT NOT NULL,
                    response_size BIGINT DEFAULT 0 NOT NULL,
                    error_code VARCHAR(64),
                    error_message VARCHAR(512),
                    log_day VARCHAR(8) NOT NULL,
                    created_at TIMESTAMP NOT NULL
                )
                """);
    }

    private JdbcDataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:billing-controller;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        return dataSource;
    }
    @Test
    void aggregationMatchesMultipleInvokes() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
        createInvokeLogTable(jdbcTemplate);
        JdbcServiceInvokeLogRepository invokeLogRepository = new JdbcServiceInvokeLogRepository(jdbcTemplate);
        invokeLogRepository.save(new ServiceInvokeLog("t1", "svc-risk", "c-agg", "p1", "ak", "h1", 200, 5, 10, null, null, Instant.now()));
        invokeLogRepository.save(new ServiceInvokeLog("t2", "svc-risk", "c-agg", "p1", "ak", "h2", 200, 5, 10, null, null, Instant.now()));
        invokeLogRepository.save(new ServiceInvokeLog("t3", "svc-risk", "c-agg", "p1", "ak", "h3", 200, 5, 10, null, null, Instant.now()));
        var ruleRepository = new InMemoryBillingRuleRepository();
        ruleRepository.save(new BillingController.CreateRuleRequest(
                "count-agg", "count-agg", BillingModel.BY_COUNT, TargetType.CONSUMER,
                com.platform.billing.bill.BillGenerator.stableTargetId("c-agg"), new BigDecimal("1.00"),
                "CNY", LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), 0).toRule(null));
        var billRepository = new InMemoryBillRepository();
        var billItemRepository = new InMemoryBillItemRepository();
        var engine = BillingRuleEngine.defaultEngine(ruleRepository);
        var billGenerator = new BillGenerator(engine, billRepository, billItemRepository, invokeLogRepository::findAll);
        var billService = new BillService(billRepository, new BillStateMachine());
        BillingController controller = new BillingController(ruleRepository, billGenerator, billRepository, billItemRepository, billService);

        var bill = controller.generate(new BillingController.GenerateBillRequest(
                BillType.EXPENSE, BillPeriod.DAILY, LocalDate.now(), LocalDate.now())).data();

        assertEquals(new BigDecimal("3.0000"), bill.totalAmount());
    }
}
