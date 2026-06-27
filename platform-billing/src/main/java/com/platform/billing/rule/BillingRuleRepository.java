package com.platform.billing.rule;

import java.time.LocalDate;
import java.util.List;

public interface BillingRuleRepository {
    List<BillingRule> activeRules(LocalDate billingDate);

    BillingRule save(BillingRule rule);

    List<BillingRule> findAll();
}
