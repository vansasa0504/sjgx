package com.platform.pipeline.storage.lifecycle;

import java.time.Instant;

public record LifecycleEvent(String assetCode, LifecycleAction action, Instant operatedAt) {
}
