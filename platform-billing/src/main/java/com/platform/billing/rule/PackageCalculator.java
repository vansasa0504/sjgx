package com.platform.billing.rule;

import com.platform.billing.model.BillingModel;
import java.math.BigDecimal;

public class PackageCalculator implements BillingCalculator {
    @Override
    public BillingModel model() {
        return BillingModel.BY_PACKAGE;
    }

    @Override
    public BigDecimal calculate(BillingUsage usage, BillingRule rule) {
        return rule.unitPrice();
    }
}