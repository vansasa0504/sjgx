package com.platform.pipeline.storage;

import com.platform.pipeline.storage.cache.CachePolicy;
import com.platform.pipeline.storage.cache.LfuCacheService;
import com.platform.pipeline.storage.etl.DataAsset;
import com.platform.pipeline.storage.etl.EtlPipeline;
import com.platform.pipeline.storage.etl.InMemoryDataAssetRepository;
import com.platform.pipeline.storage.lifecycle.DataLifecycleManager;
import com.platform.pipeline.storage.lifecycle.LifecycleAction;
import com.platform.pipeline.storage.lifecycle.LifecyclePolicy;
import com.platform.pipeline.storage.marketplace.DataMarketplace;
import com.platform.pipeline.storage.marketplace.InMemoryMarketplaceRepository;
import com.platform.pipeline.storage.tier.InMemoryTierStorageStore;
import com.platform.pipeline.storage.tier.JdbcWarmStorageStore;
import com.platform.pipeline.storage.tier.LocalColdStorageStore;
import com.platform.pipeline.storage.tier.StoragePolicy;
import com.platform.pipeline.storage.tier.StorageTier;
import com.platform.pipeline.storage.tier.TieredStorageRouter;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void lfuCacheTracksHitsEvictsLeastUsedAndExpiresEntries() {
        MutableClock clock = new MutableClock();
        LfuCacheService cache = new LfuCacheService(new CachePolicy(1_000, 2), clock);

        cache.put("a", "A");
        cache.put("b", "B");
        assertEquals("A", cache.get("a").orElseThrow());
        cache.put("c", "C");

        assertTrue(cache.get("a").isPresent());
        assertFalse(cache.get("b").isPresent());
        clock.advanceMillis(1_001);
        assertFalse(cache.get("a").isPresent());
    }

    @Test
    void routesStorageByPolicyAndWritesWarmAndColdStores() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:warm-storage;MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcWarmStorageStore warmStore = new JdbcWarmStorageStore(dataSource);
        LocalColdStorageStore coldStore = new LocalColdStorageStore(tempDir);
        TieredStorageRouter router = new TieredStorageRouter(
                new StoragePolicy("policy-a", 3, 2, "MINIO", true),
                new InMemoryTierStorageStore(), warmStore, coldStore);

        assertEquals(StorageTier.COLD, router.store("asset-a", "v1").tier());
        assertEquals(StorageTier.WARM, router.store("asset-a", "v2").tier());
        assertEquals(StorageTier.HOT, router.store("asset-a", "v3").tier());

        assertTrue(Files.exists(tempDir.resolve("asset-a.data")));
        assertEquals(1, router.records(StorageTier.WARM).size());
        assertEquals("v2", router.records(StorageTier.WARM).get(0).payload());
        assertEquals(1, router.records(StorageTier.HOT).size());
    }

    @Test
    void etlPublishesTaggedAssetsToEncryptedMarketplaceAndAssetRepository() {
        EtlPipeline pipeline = new EtlPipeline();
        DataAsset asset = pipeline.process(
                Map.of("id", " 001 ", "risk_score", "90", "phone", "13800138000"),
                Set.of("id"),
                Map.of("id", "customerId"),
                Map.of("source", "partner-a"));
        StorageCipher cipher = new StorageCipher("test-key");
        InMemoryDataAssetRepository assetRepository = new InMemoryDataAssetRepository(cipher);
        DataMarketplace marketplace = new DataMarketplace(new InMemoryMarketplaceRepository(cipher));

        assetRepository.save(asset);
        marketplace.publish(asset, "partner-a");

        assertEquals("001", asset.fields().get("customerId"));
        assertTrue(asset.tags().contains("CUSTOMER"));
        assertTrue(asset.tags().contains("RISK"));
        assertTrue(asset.tags().contains("PII"));
        assertEquals(1, marketplace.searchByTag("RISK").size());
        assertNotEquals(asset.fields().toString(), assetRepository.storedFields(asset.assetCode()));
        assertNotEquals(asset.fields().toString(), marketplace.storedFields(asset.assetCode()));
        assertEquals(asset.fields().toString(), cipher.decrypt(assetRepository.storedFields(asset.assetCode())));
        assertEquals(asset.fields().toString(), cipher.decrypt(marketplace.storedFields(asset.assetCode())));
    }

    @Test
    void lifecycleArchivesAndDestroysExpiredAssetsWithAuditEvents() {
        MutableClock clock = new MutableClock();
        DataLifecycleManager manager = new DataLifecycleManager(
                new LifecyclePolicy(Duration.ofDays(10), Duration.ofDays(30)), clock);
        DataAsset fresh = new DataAsset("asset-fresh", Map.of("id", "1"), Set.of(), clock.instant());
        DataAsset old = new DataAsset("asset-old", Map.of("id", "2"), Set.of(), clock.instant().minus(Duration.ofDays(15)));
        DataAsset expired = new DataAsset("asset-expired", Map.of("id", "3"), Set.of(), clock.instant().minus(Duration.ofDays(31)));

        assertEquals(LifecycleAction.KEEP, manager.scan(fresh));
        assertEquals(LifecycleAction.ARCHIVE, manager.scan(old));
        assertEquals(LifecycleAction.DESTROY, manager.scan(expired));
        assertEquals(3, manager.events().size());
    }

    private static class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-06-25T00:00:00Z");

        void advanceMillis(long millis) {
            instant = instant.plusMillis(millis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
