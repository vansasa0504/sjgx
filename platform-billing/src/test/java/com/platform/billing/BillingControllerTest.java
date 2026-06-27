package com.platform.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.platform.billing.bill.BillGenerator;
import com.platform.billing.bill.BillService;
import com.platform.billing.bill.BillStateMachine;
import com.platform.billing.bill.InMemoryBillRepository;
import com.platform.billing.model.BillingModel;
import com.platform.billing.model.TargetType;
import com.platform.billing.rule.BillingRuleEngine;
import com.platform.billing.rule.InMemoryBillingRuleRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BillingControllerTest {
    @Test
    void createsAndListsRule() {
        var ruleRepository = new InMemoryBillingRuleRepository();
        var billRepository = new InMemoryBillRepository();
        var engine = BillingRuleEngine.defaultEngine(ruleRepository);
        var billGenerator = new BillGenerator(engine, billRepository);
        var billService = new BillService(billRepository, new BillStateMachine());
        BillingController controller = new BillingController(ruleRepository, billGenerator, billRepository, billService);

        var rule = controller.createRule(new BillingController.CreateRuleRequest(
                "count-c1", "count-c1", BillingModel.BY_COUNT, TargetType.CONSUMER, 1L,
                new BigDecimal("1.00"), "CNY", LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), 0)).data();
        assertEquals("count-c1", rule.ruleCode());
        assertTrue(controller.listRules(null).data().stream().anyMatch(r -> "count-c1".equals(r.ruleCode())));
    }
}
