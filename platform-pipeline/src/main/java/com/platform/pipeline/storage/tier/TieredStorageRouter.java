package com.platform.pipeline.storage.tier;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TieredStorageRouter {
    private final StoragePolicy policy;
    private final Map<String, Integer> accessCounts = new ConcurrentHashMap<>();
    private final Map<StorageTier, TierStorageStore> stores = new EnumMap<>(StorageTier.class);

    public TieredStorageRouter(int hotThreshold, int warmThreshold) {
        this(new StoragePolicy("default", hotThreshold, warmThreshold, "MINIO", true),
                new InMemoryTierStorageStore(), new InMemoryTierStorageStore(), new InMemoryTierStorageStore());
    }

    public TieredStorageRouter(StoragePolicy policy) {
        this(policy, new InMemoryTierStorageStore(), new InMemoryTierStorageStore(), new InMemoryTierStorageStore());
    }

    public TieredStorageRouter(StoragePolicy policy, TierStorageStore hotStore, WarmStorageStore warmStore, ColdStorageStore coldStore) {
        this(policy, hotStore, (TierStorageStore) warmStore, (TierStorageStore) coldStore);
    }

    public TieredStorageRouter(StoragePolicy policy, TierStorageStore hotStore, TierStorageStore warmStore, TierStorageStore coldStore) {
        this.policy = policy;
        stores.put(StorageTier.HOT, hotStore);
        stores.put(StorageTier.WARM, warmStore);
        stores.put(StorageTier.COLD, coldStore);
    }

    public TieredRecord store(String key, String payload) {
        StorageTier tier = route(key);
        TieredRecord record = new TieredRecord(key, payload, tier);
        stores.get(tier).write(record);
        return record;
    }

    public StorageTier route(String key) {
        int count = accessCounts.merge(key, 1, Integer::sum);
        if (count >= policy.hotThreshold()) {
            return StorageTier.HOT;
        }
        if (count >= policy.warmThreshold()) {
            return StorageTier.WARM;
        }
        return StorageTier.COLD;
    }

    public List<TieredRecord> records(StorageTier tier) {
        return stores.get(tier).readAll();
    }
}
