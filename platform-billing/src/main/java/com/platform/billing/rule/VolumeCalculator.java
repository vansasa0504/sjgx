package com.platform.billing.rule;

import com.platform.billing.model.BillingModel;
import java.math.BigDecimal;

public class VolumeCalculator implements BillingCalculator {
    @Override
    public BillingModel model() {
        return BillingModel.BY_VOLUME;
    }

    @Override
    public BigDecimal calculate(BillingUsage usage, BillingRule rule) {
        return rule.unitPrice().multiply(BigDecimal.valueOf(usage.volumeBytes()));
    }
}