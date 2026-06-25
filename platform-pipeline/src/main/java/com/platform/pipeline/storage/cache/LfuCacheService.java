package com.platform.pipeline.storage.cache;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class LfuCacheService {
    private final CachePolicy policy;
    private final Clock clock;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final AtomicLong requests = new AtomicLong();
    private final AtomicLong hits = new AtomicLong();

    public LfuCacheService(CachePolicy policy) {
        this(policy, Clock.systemUTC());
    }

    public LfuCacheService(CachePolicy policy, Clock clock) {
        this.policy = policy;
        this.clock = clock;
    }

    public void put(String key, String value) {
        evictExpired();
        if (cache.size() >= policy.maxEntries() && !cache.containsKey(key)) {
            cache.entrySet().stream().min(Comparator.comparingLong(entry -> entry.getValue().hits()))
                    .ifPresent(entry -> cache.remove(entry.getKey()));
        }
        cache.put(key, new CacheEntry(value, Instant.ofEpochMilli(clock.millis() + policy.ttlMillis()), 0));
    }

    public Optional<String> get(String key) {
        requests.incrementAndGet();
        CacheEntry entry = cache.get(key);
        if (entry == null || entry.expiresAt().isBefore(clock.instant())) {
            cache.remove(key);
            return Optional.empty();
        }
        hits.incrementAndGet();
        cache.put(key, entry.hit());
        return Optional.of(entry.value());
    }

    public void invalidate(String key) {
        cache.remove(key);
    }

    public double hitRate() {
        long total = requests.get();
        return total == 0 ? 0 : hits.get() / (double) total;
    }

    public int size() {
        evictExpired();
        return cache.size();
    }

    private void evictExpired() {
        Instant now = clock.instant();
        cache.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }
}
