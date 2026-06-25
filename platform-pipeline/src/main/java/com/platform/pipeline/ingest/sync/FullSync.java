package com.platform.pipeline.ingest.sync;

import java.util.List;

public class FullSync implements SyncStrategy {
    @Override
    public String mode() { return "FULL"; }

    @Override
    public SyncResult sync(List<String> sourceRecords, String checkpointKey, OffsetStore offsetStore) {
        offsetStore.put(checkpointKey, sourceRecords.size());
        return new SyncResult(List.copyOf(sourceRecords), sourceRecords.size());
    }
}
