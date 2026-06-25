package com.platform.pipeline.storage.etl;

import com.platform.pipeline.storage.StorageCipher;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryDataAssetRepository implements DataAssetRepository {
    private final StorageCipher cipher;
    private final Map<String, StoredAsset> assets = new ConcurrentHashMap<>();

    public InMemoryDataAssetRepository(StorageCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public void save(DataAsset asset) {
        assets.put(asset.assetCode(), new StoredAsset(asset, cipher.encrypt(asset.fields().toString())));
    }

    @Override
    public DataAsset get(String assetCode) {
        StoredAsset stored = assets.get(assetCode);
        return stored == null ? null : stored.asset();
    }

    @Override
    public String storedFields(String assetCode) {
        StoredAsset stored = assets.get(assetCode);
        return stored == null ? null : stored.encryptedFields();
    }

    @Override
    public java.util.List<DataAsset> list() {
        return assets.values().stream().map(StoredAsset::asset).toList();
    }

    private record StoredAsset(DataAsset asset, String encryptedFields) {
    }
}
