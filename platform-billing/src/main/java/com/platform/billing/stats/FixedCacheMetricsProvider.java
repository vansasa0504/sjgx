package com.platform.billing.stats;

import java.math.BigDecimal;

public class FixedCacheMetricsProvider implements CacheMetricsProvider {
    private final BigDecimal hitRate;

    public FixedCacheMetricsProvider(BigDecimal hitRate) {
        this.hitRate = hitRate;
    }

    @Override
    public BigDecimal hitRate() {
        return hitRate;
    }
}