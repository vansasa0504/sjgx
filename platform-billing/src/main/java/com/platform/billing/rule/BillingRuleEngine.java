package com.platform.billing.rule;

import com.platform.billing.model.BillingModel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class BillingRuleEngine {
    private final BillingRuleRepository repository;
    private final Map<BillingModel, BillingCalculator> calculators = new EnumMap<>(BillingModel.class);

    public BillingRuleEngine(BillingRuleRepository repository, List<BillingCalculator> calculators) {
        this.repository = repository;
        calculators.forEach(calculator -> this.calculators.put(calculator.model(), calculator));
    }

    public BigDecimal calculate(BillingUsage usage, LocalDate billingDate) {
        List<BillingRule> matched = repository.activeRules(billingDate).stream()
                .filter(rule -> rule.targetType() == usage.targetType())
                .filter(rule -> rule.targetId() == null || rule.targetId().equals(usage.targetId()))
                .toList();
        long allowance = matched.stream()
                .filter(rule -> rule.billingModel() == BillingModel.BY_PACKAGE)
                .mapToLong(BillingRule::packageAllowance)
                .sum();
        BillingUsage adjustedUsage = usage.withPackageAllowance(allowance);
        return matched.stream()
                .map(rule -> calculators.get(rule.billingModel()).calculate(adjustedUsage, rule))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, java.math.RoundingMode.HALF_UP);
    }

    public static BillingRuleEngine defaultEngine(BillingRuleRepository repository) {
        return new BillingRuleEngine(repository, List.of(new CountCalculator(), new VolumeCalculator(),
                new InterfaceCalculator(), new PackageCalculator(), new DurationCalculator()));
    }
}