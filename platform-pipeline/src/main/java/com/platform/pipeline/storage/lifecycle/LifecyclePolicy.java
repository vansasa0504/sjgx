package com.platform.pipeline.storage.lifecycle;

import java.time.Duration;

public record LifecyclePolicy(Duration archiveAfter, Duration destroyAfter) {
    public LifecyclePolicy {
        if (archiveAfter.isNegative() || destroyAfter.isNegative() || destroyAfter.compareTo(archiveAfter) < 0) {
            throw new IllegalArgumentException("invalid lifecycle policy");
        }
    }
}
