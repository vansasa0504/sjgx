package com.platform.billing.rule;

import com.platform.billing.model.TargetType;

public record BillingUsage(
        TargetType targetType,
        Long targetId,
        String serviceCode,
        String consumerCode,
        long invokeCount,
        long volumeBytes,
        long interfaceCount,
        long durationSeconds,
        long packageAllowance
) {
    public BillingUsage withPackageAllowance(long allowance) {
        return new BillingUsage(targetType, targetId, serviceCode, consumerCode, invokeCount, volumeBytes, interfaceCount, durationSeconds, allowance);
    }
}