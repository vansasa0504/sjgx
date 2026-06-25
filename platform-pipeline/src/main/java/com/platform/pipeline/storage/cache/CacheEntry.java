package com.platform.pipeline.storage.cache;

import java.time.Instant;

record CacheEntry(String value, Instant expiresAt, long hits) {
    CacheEntry hit() {
        return new CacheEntry(value, expiresAt, hits + 1);
    }
}
