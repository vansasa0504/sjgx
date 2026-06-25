package com.platform.pipeline.service;

import com.platform.common.exception.BusinessException;

import java.time.Clock;
import java.util.function.Supplier;

public class CircuitBreaker {
    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final long coolDownMillis;
    private final Clock clock;
    private int failures;
    private long openedAt;
    private State state = State.CLOSED;

    public CircuitBreaker() {
        this(3, 30_000, Clock.systemUTC());
    }

    public CircuitBreaker(int failureThreshold, long coolDownMillis, Clock clock) {
        this.failureThreshold = failureThreshold;
        this.coolDownMillis = coolDownMillis;
        this.clock = clock;
    }

    public synchronized <T> T call(Supplier<T> supplier) {
        if (state == State.OPEN) {
            if (clock.millis() - openedAt < coolDownMillis) {
                throw new BusinessException("SERVICE-503", "circuit breaker open");
            }
            state = State.HALF_OPEN;
        }
        try {
            T result = supplier.get();
            failures = 0;
            state = State.CLOSED;
            return result;
        } catch (RuntimeException ex) {
            failures++;
            if (state == State.HALF_OPEN || failures >= failureThreshold) {
                state = State.OPEN;
                openedAt = clock.millis();
            }
            throw new BusinessException("SERVICE-503", "service fallback triggered");
        }
    }

    public synchronized State state() { return state; }
}
