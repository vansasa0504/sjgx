package com.platform.pipeline.storage.marketplace;

import com.platform.pipeline.storage.StorageCipher;
import com.platform.pipeline.storage.etl.DataAsset;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryMarketplaceRepository implements MarketplaceRepository {
    private final StorageCipher cipher;
    private final Map<String, StoredMarketplaceData> assets = new ConcurrentHashMap<>();

    public InMemoryMarketplaceRepository(StorageCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public void save(DataAsset asset, String source) {
        assets.put(asset.assetCode(), new StoredMarketplaceData(asset, source, cipher.encrypt(asset.fields().toString())));
    }

    @Override
    public DataAsset get(String assetCode) {
        StoredMarketplaceData stored = assets.get(assetCode);
        return stored == null ? null : stored.asset();
    }

    @Override
    public String storedFields(String assetCode) {
        StoredMarketplaceData stored = assets.get(assetCode);
        return stored == null ? null : stored.encryptedFields();
    }

    @Override
    public java.util.List<DataAsset> list() {
        return assets.values().stream().map(StoredMarketplaceData::asset).toList();
    }

    private record StoredMarketplaceData(DataAsset asset, String source, String encryptedFields) {
    }
}
