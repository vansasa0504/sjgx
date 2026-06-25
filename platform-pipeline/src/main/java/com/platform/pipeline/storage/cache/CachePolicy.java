package com.platform.pipeline.storage.cache;

public record CachePolicy(long ttlMillis, int maxEntries) {
    public CachePolicy {
        if (ttlMillis <= 0 || maxEntries <= 0) {
            throw new IllegalArgumentException("cache policy must be positive");
        }
    }
}
