package com.platform.pipeline.service;

import com.platform.common.exception.BusinessException;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimiter {
    private final long maxCalls;
    private final long windowMillis;
    private final Clock clock;
    private final Map<String, Deque<Long>> calls = new ConcurrentHashMap<>();

    public RateLimiter(long maxCalls) {
        this(maxCalls, 60_000, Clock.systemUTC());
    }

    public RateLimiter(long maxCalls, long windowMillis, Clock clock) {
        this.maxCalls = maxCalls;
        this.windowMillis = windowMillis;
        this.clock = clock;
    }

    public synchronized void acquire(String key) {
        long now = clock.millis();
        Deque<Long> timestamps = calls.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= windowMillis) {
            timestamps.removeFirst();
        }
        if (timestamps.size() >= maxCalls) {
            throw new BusinessException("SERVICE-429", "rate limit exceeded");
        }
        timestamps.addLast(now);
    }
}
