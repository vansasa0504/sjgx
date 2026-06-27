package com.platform.billing.rule;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryBillingRuleRepository implements BillingRuleRepository {
    private final AtomicLong ids = new AtomicLong(1);
    private final List<BillingRule> rules = new CopyOnWriteArrayList<>();

    public InMemoryBillingRuleRepository(Collection<BillingRule> rules) {
        this.rules.addAll(rules);
    }

    public InMemoryBillingRuleRepository() {
    }

    @Override
    public List<BillingRule> activeRules(LocalDate billingDate) {
        return rules.stream().filter(rule -> rule.activeOn(billingDate)).toList();
    }

    @Override
    public BillingRule save(BillingRule rule) {
        long id = rule.id() == null ? ids.getAndIncrement() : rule.id();
        BillingRule saved = new BillingRule(id, rule.ruleCode(), rule.ruleName(), rule.billingModel(),
                rule.targetType(), rule.targetId(), rule.unitPrice(), rule.currency(), rule.effectiveFrom(),
                rule.effectiveTo(), rule.status(), rule.packageAllowance());
        rules.removeIf(existing -> existing.id() != null && existing.id() == id);
        rules.add(saved);
        return saved;
    }

    @Override
    public List<BillingRule> findAll() {
        return new ArrayList<>(rules);
    }
}
