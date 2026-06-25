package com.platform.pipeline.ingest.sync;

public interface OffsetStore {
    long get(String key);
    void put(String key, long offset);
}
