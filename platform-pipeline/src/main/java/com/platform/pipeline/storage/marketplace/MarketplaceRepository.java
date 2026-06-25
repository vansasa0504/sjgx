package com.platform.pipeline.storage.marketplace;

import com.platform.pipeline.storage.etl.DataAsset;

import java.util.List;

public interface MarketplaceRepository {
    void save(DataAsset asset, String source);

    DataAsset get(String assetCode);

    String storedFields(String assetCode);

    List<DataAsset> list();
}
