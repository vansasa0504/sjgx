package com.platform.billing.stats;

import java.math.BigDecimal;

public interface CacheMetricsProvider {
    BigDecimal hitRate();
}