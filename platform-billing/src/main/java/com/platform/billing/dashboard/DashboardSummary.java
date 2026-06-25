package com.platform.billing.dashboard;

import java.math.BigDecimal;

public record DashboardSummary(
        long runningServices,
        long invokeCount,
        BigDecimal successRate,
        BigDecimal complianceScore,
        BigDecimal costAmount
) {
}