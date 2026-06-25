package com.platform.pipeline.ingest.sync;

import java.util.List;

public record SyncResult(List<String> records, long nextOffset) {
}
