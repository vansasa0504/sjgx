package com.platform.billing.rule;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class InMemoryBillingRuleRepository implements BillingRuleRepository {
    private final List<BillingRule> rules = new ArrayList<>();

    public InMemoryBillingRuleRepository(Collection<BillingRule> rules) {
        this.rules.addAll(rules);
    }

    @Override
    public List<BillingRule> activeRules(LocalDate billingDate) {
        return rules.stream().filter(rule -> rule.activeOn(billingDate)).toList();
    }
}