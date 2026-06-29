package com.platform.pipeline.ingest;

import java.time.Instant;

public record OffsetCheckpoint(long taskId, String connectorType, long offset, String checkpointJson, Instant updatedAt) {
}
