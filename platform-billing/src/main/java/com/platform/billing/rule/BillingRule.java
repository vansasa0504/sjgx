package com.platform.billing.rule;

import com.platform.billing.model.BillingModel;
import com.platform.billing.model.TargetType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record BillingRule(
        Long id,
        String ruleCode,
        String ruleName,
        BillingModel billingModel,
        TargetType targetType,
        Long targetId,
        BigDecimal unitPrice,
        String currency,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String status,
        long packageAllowance
) {
    public BillingRule {
        if (ruleCode == null || ruleCode.isBlank()) {
            throw new IllegalArgumentException("ruleCode must not be blank");
        }
        unitPrice = Objects.requireNonNull(unitPrice, "unitPrice");
        currency = Objects.requireNonNullElse(currency, "CNY");
        status = Objects.requireNonNullElse(status, "ACTIVE");
        effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom");
    }

    public boolean activeOn(LocalDate date) {
        return "ACTIVE".equals(status) && !date.isBefore(effectiveFrom) && (effectiveTo == null || !date.isAfter(effectiveTo));
    }
}