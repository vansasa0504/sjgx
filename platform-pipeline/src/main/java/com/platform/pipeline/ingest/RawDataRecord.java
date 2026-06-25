package com.platform.pipeline.ingest;

import java.time.Instant;
import java.util.Map;

public record RawDataRecord(long taskId, long partnerId, Map<String, String> payload, Instant createdAt) {
}
