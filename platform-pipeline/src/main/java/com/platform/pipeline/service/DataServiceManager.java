package com.platform.pipeline.service;

import com.platform.common.exception.BusinessException;
import com.platform.common.model.Page;
import com.platform.common.model.ServiceInvokeLog;
import com.platform.common.security.SignatureUtil;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class DataServiceManager {
    private final AtomicLong ids = new AtomicLong(1);
    private final Map<String, DataServiceDefinition> services = new ConcurrentHashMap<>();
    private final Map<String, String> routeData = new ConcurrentHashMap<>();
    private final DataServiceStateMachine stateMachine = new DataServiceStateMachine();
    private final AsyncInvokeLogWriter logWriter = new AsyncInvokeLogWriter();
    private final RateLimiter rateLimiter = new RateLimiter(2);
    private final CircuitBreaker circuitBreaker = new CircuitBreaker();
    private final SignatureUtil signatureUtil = new SignatureUtil(Clock.systemUTC());
    private final ApiCredentialRepository apiCredentialRepository;

    public DataServiceManager() {
        this(new ApiCredentialRepository());
    }

    public DataServiceManager(ApiCredentialRepository apiCredentialRepository) {
        this.apiCredentialRepository = apiCredentialRepository;
    }

    public DataServiceDefinition register(String serviceCode, String name, String routeKey) {
        DataServiceDefinition definition = new DataServiceDefinition(ids.getAndIncrement(), serviceCode, name, routeKey);
        services.put(serviceCode, definition);
        routeData.putIfAbsent(routeKey, "{\"status\":\"ok\"}");
        return definition;
    }

    public List<DataServiceDefinition> list(String keyword, String status) {
        return services.values().stream()
                .filter(d -> keyword == null || keyword.isBlank()
                        || (d.serviceCode() != null && d.serviceCode().contains(keyword))
                        || (d.name() != null && d.name().contains(keyword)))
                .filter(d -> status == null || status.isBlank() || status.equals(d.status().name()))
                .toList();
    }

    public DataServiceDefinition detail(String serviceCode) {
        return require(serviceCode);
    }

    public DataServiceDefinition update(String serviceCode, String name, String routeKey) {
        DataServiceDefinition definition = require(serviceCode);
        if (name != null) {
            definition.name(name);
        }
        if (routeKey != null) {
            definition.routeKey(routeKey);
            routeData.putIfAbsent(routeKey, "{\"status\":\"ok\"}");
        }
        return definition;
    }

    public Page<ServiceInvokeLog> logs(String serviceCode, String consumerId, String status, int page, int size) {
        List<ServiceInvokeLog> filtered = logWriter.logs().stream()
                .filter(l -> serviceCode == null || serviceCode.isBlank() || serviceCode.equals(l.serviceCode()))
                .filter(l -> consumerId == null || consumerId.isBlank() || consumerId.equals(l.consumerCode()))
                .filter(l -> status == null || status.isBlank() || status.equals(String.valueOf(l.status())))
                .toList();
        int safeSize = size <= 0 ? 10 : size;
        int safePage = page <= 0 ? 1 : page;
        int from = Math.min((safePage - 1) * safeSize, filtered.size());
        int to = Math.min(from + safeSize, filtered.size());
        return Page.of(new ArrayList<>(filtered.subList(from, to)), filtered.size(), safePage, safeSize);
    }

    public DataServiceDefinition apply(String serviceCode, DataServiceEvent event) {
        DataServiceDefinition definition = require(serviceCode);
        definition.status(stateMachine.transit(definition.status(), event));
        if (event == DataServiceEvent.VERSION) {
            definition.incrementVersion();
        }
        return definition;
    }

    public void putRouteData(String routeKey, String response) {
        routeData.put(routeKey, response);
    }

    /**
     * 调用数据服务。通过 apiKey 从仓储查找 secret 进行签名验证，不再从请求体接收明文 secret。
     */
    public String invoke(String serviceCode, String consumerCode, String apiKey,
                         long timestamp, String nonce, String body, String signature) {
        long start = System.currentTimeMillis();
        ApiCredentialRepository.ApiCredential credential = apiCredentialRepository.findByApiKey(apiKey);
        String secret = credential.secret();
        signatureUtil.verify(apiKey, secret, timestamp, nonce, body, signature);
        String effectiveConsumer = consumerCode != null ? consumerCode : credential.consumerCode();
        rateLimiter.acquire(effectiveConsumer + ':' + serviceCode);
        DataServiceDefinition definition = require(serviceCode);
        if (definition.status() != DataServiceStatus.PUBLISHED) {
            throw new BusinessException("SERVICE-404", "service not published");
        }
        String result = circuitBreaker.call(() -> routeData.get(definition.routeKey()));
        if (result == null) {
            throw new BusinessException("SERVICE-404", "route not found");
        }
        logWriter.write(new ServiceInvokeLog(serviceCode, effectiveConsumer, null, 200, System.currentTimeMillis() - start, ServiceInvokeLog.bytesOf(result), Instant.now()));
        return result;
    }

    public AsyncInvokeLogWriter logWriter() { return logWriter; }
    public SignatureUtil signatureUtil() { return signatureUtil; }
    public ApiCredentialRepository apiCredentialRepository() { return apiCredentialRepository; }

    private DataServiceDefinition require(String serviceCode) {
        DataServiceDefinition definition = services.get(serviceCode);
        if (definition == null) {
            throw new BusinessException("SERVICE-404", "service not found");
        }
        return definition;
    }
}