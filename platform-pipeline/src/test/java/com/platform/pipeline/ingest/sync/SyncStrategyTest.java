package com.platform.pipeline.ingest.sync;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SyncStrategyTest {
    @Test
    void resumesIncrementalSyncWithoutDuplicates() {
        InMemoryOffsetStore store = new InMemoryOffsetStore();
        IncrementalSync sync = new IncrementalSync();

        SyncResult first = sync.sync(List.of("a", "b"), "task-1", store);
        SyncResult second = sync.sync(List.of("a", "b", "c", "d"), "task-1", store);

        assertEquals(List.of("a", "b"), first.records());
        assertEquals(List.of("c", "d"), second.records());
        assertEquals(4, second.nextOffset());
    }

    @Test
    void supportsFullRealtimeAndScheduledModes() {
        InMemoryOffsetStore store = new InMemoryOffsetStore();

        assertEquals(2, new FullSync().sync(List.of("a", "b"), "full", store).records().size());
        assertEquals("REALTIME", new RealtimeSync().mode());
        assertEquals("SCHEDULED", new ScheduledSync().mode());
    }
}
