package com.platform.pipeline.storage.tier;

public record StoragePolicy(String policyCode, int hotThreshold, int warmThreshold, String coolTarget, boolean enabled) {
    public StoragePolicy {
        if (hotThreshold <= 0 || warmThreshold <= 0 || hotThreshold < warmThreshold) {
            throw new IllegalArgumentException("invalid storage thresholds");
        }
    }
}
