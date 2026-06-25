package com.platform.pipeline.ingest.sync;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryOffsetStore implements OffsetStore {
    private final Map<String, Long> offsets = new ConcurrentHashMap<>();

    @Override
    public long get(String key) { return offsets.getOrDefault(key, 0L); }

    @Override
    public void put(String key, long offset) { offsets.put(key, offset); }
}
