package com.platform.pipeline.storage;

import com.platform.common.security.Sm4Util;

public class StorageCipher {
    private final String key;

    public StorageCipher(String key) {
        this.key = key;
    }

    public static StorageCipher fromEnvironment() {
        String key = System.getenv().getOrDefault("DATA_ASSET_SM4_KEY", "0123456789abcdef");
        return new StorageCipher(key);
    }

    public String encrypt(String plainText) {
        return Sm4Util.encrypt(plainText, key);
    }

    public String decrypt(String cipherText) {
        return Sm4Util.decrypt(cipherText, key);
    }
}
