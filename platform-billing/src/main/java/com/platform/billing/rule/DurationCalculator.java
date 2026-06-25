package com.platform.billing.rule;

import com.platform.billing.model.BillingModel;
import java.math.BigDecimal;

public class DurationCalculator implements BillingCalculator {
    @Override
    public BillingModel model() {
        return BillingModel.BY_DURATION;
    }

    @Override
    public BigDecimal calculate(BillingUsage usage, BillingRule rule) {
        return rule.unitPrice().multiply(BigDecimal.valueOf(usage.durationSeconds()));
    }
}