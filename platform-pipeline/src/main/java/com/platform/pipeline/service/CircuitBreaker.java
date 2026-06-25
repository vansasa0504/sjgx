package com.platform.pipeline.service;

import com.platform.common.exception.BusinessException;

import java.util.function.Supplier;

public class CircuitBreaker {
    public <T> T call(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException ex) {
            throw new BusinessException("SERVICE-503", "service fallback triggered");
        }
    }
}
