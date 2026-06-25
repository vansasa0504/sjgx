package com.platform.pipeline.storage.etl;

import com.platform.pipeline.storage.StorageCipher;

import java.util.List;

public interface DataAssetRepository {
    void save(DataAsset asset);

    DataAsset get(String assetCode);

    String storedFields(String assetCode);

    List<DataAsset> list();
}
