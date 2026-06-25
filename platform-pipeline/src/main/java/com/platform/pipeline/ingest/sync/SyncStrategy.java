package com.platform.pipeline.ingest.sync;

import java.util.List;

public interface SyncStrategy {
    String mode();
    SyncResult sync(List<String> sourceRecords, String checkpointKey, OffsetStore offsetStore);
}
