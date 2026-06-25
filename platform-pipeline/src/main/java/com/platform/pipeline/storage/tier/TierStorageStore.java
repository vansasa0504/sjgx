package com.platform.pipeline.storage.tier;

import java.util.List;

public interface TierStorageStore {
    void write(TieredRecord record);

    List<TieredRecord> readAll();
}
