package com.platform.pipeline.ingest.sync;

import java.util.List;

public class IncrementalSync implements SyncStrategy {
    @Override
    public String mode() { return "INCREMENTAL"; }

    @Override
    public SyncResult sync(List<String> sourceRecords, String checkpointKey, OffsetStore offsetStore) {
        long offset = offsetStore.get(checkpointKey);
        List<String> records = sourceRecords.stream().skip(offset).toList();
        long next = sourceRecords.size();
        offsetStore.put(checkpointKey, next);
        return new SyncResult(records, next);
    }
}
