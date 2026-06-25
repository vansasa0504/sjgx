package com.platform.pipeline.storage.tier;

import java.util.ArrayList;
import java.util.List;

public class InMemoryTierStorageStore implements TierStorageStore {
    private final List<TieredRecord> records = new ArrayList<>();

    @Override
    public void write(TieredRecord record) {
        records.add(record);
    }

    @Override
    public List<TieredRecord> readAll() {
        return List.copyOf(records);
    }
}
