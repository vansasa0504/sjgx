package com.platform.billing.rule;

import com.platform.billing.model.BillingModel;
import java.math.BigDecimal;

public interface BillingCalculator {
    BillingModel model();

    BigDecimal calculate(BillingUsage usage, BillingRule rule);
}