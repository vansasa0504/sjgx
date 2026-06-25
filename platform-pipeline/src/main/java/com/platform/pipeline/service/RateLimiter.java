package com.platform.pipeline.service;

import com.platform.common.exception.BusinessException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimiter {
    private final long maxCalls;
    private final Map<String, Long> counts = new ConcurrentHashMap<>();

    public RateLimiter(long maxCalls) { this.maxCalls = maxCalls; }

    public void acquire(String key) {
        long count = counts.merge(key, 1L, Long::sum);
        if (count > maxCalls) {
            throw new BusinessException("SERVICE-429", "rate limit exceeded");
        }
    }
}
