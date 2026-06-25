package com.platform.pipeline.storage.marketplace;

import com.platform.pipeline.storage.StorageCipher;
import com.platform.pipeline.storage.etl.DataAsset;

import java.util.List;

public class DataMarketplace {
    private final MarketplaceRepository repository;

    public DataMarketplace() {
        this(new InMemoryMarketplaceRepository(StorageCipher.fromEnvironment()));
    }

    public DataMarketplace(MarketplaceRepository repository) {
        this.repository = repository;
    }

    public void publish(DataAsset asset) {
        publish(asset, "UNKNOWN");
    }

    public void publish(DataAsset asset, String source) {
        repository.save(asset, source);
    }

    public DataAsset get(String assetCode) {
        return repository.get(assetCode);
    }

    public String storedFields(String assetCode) {
        return repository.storedFields(assetCode);
    }

    public List<DataAsset> searchByTag(String tag) {
        return repository.list().stream()
                .filter(asset -> asset.tags().contains(tag))
                .toList();
    }

    public List<DataAsset> list() {
        return repository.list();
    }
}
