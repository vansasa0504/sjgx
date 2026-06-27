package com.platform.pipeline.service;

import com.platform.common.exception.BusinessException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API Key 凭证仓储。根据 apiKey 查找对应 secret，替代调用方在请求体中明文传入 secret。
 * 当前为内存实现，生产环境应替换为 t_api_credential 表的 Jdbc 实现。
 */
public class ApiCredentialRepository {
    private final Map<String, ApiCredential> credentials = new ConcurrentHashMap<>();

    public ApiCredentialRepository() {
        // 开发/测试环境默认凭证
        save(new ApiCredential("api-key", "secret", "e2e-consumer", null));
    }

    public void save(ApiCredential credential) {
        credentials.put(credential.apiKey(), credential);
    }

    public ApiCredential findByApiKey(String apiKey) {
        ApiCredential credential = credentials.get(apiKey);
        if (credential == null) {
            throw new BusinessException("AUTH-404", "api key not found");
        }
        return credential;
    }

    public record ApiCredential(String apiKey, String secret, String consumerCode, String serviceCode) {
    }
}