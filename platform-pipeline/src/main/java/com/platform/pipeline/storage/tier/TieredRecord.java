package com.platform.pipeline.storage.tier;

public record TieredRecord(String key, String payload, StorageTier tier) {
}
