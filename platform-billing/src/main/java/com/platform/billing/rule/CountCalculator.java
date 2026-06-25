package com.platform.billing.rule;

import com.platform.billing.model.BillingModel;
import java.math.BigDecimal;

public class CountCalculator implements BillingCalculator {
    @Override
    public BillingModel model() {
        return BillingModel.BY_COUNT;
    }

    @Override
    public BigDecimal calculate(BillingUsage usage, BillingRule rule) {
        long billable = Math.max(0, usage.invokeCount() - usage.packageAllowance());
        return rule.unitPrice().multiply(BigDecimal.valueOf(billable));
    }
}