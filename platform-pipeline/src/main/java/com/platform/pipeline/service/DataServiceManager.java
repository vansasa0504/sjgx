package com.platform.pipeline.service;

import com.platform.common.exception.BusinessException;
import com.platform.common.security.SignatureUtil;

import java.time.Clock;
import java.time.Instant;
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

    public DataServiceDefinition register(String serviceCode, String name, String routeKey) {
        DataServiceDefinition definition = new DataServiceDefinition(ids.getAndIncrement(), serviceCode, name, routeKey);
        services.put(serviceCode, definition);
        return definition;
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

    public String invoke(String serviceCode, String consumerCode, String apiKey, String secret,
                         long timestamp, String nonce, String body, String signature) {
        long start = System.currentTimeMillis();
        signatureUtil.verify(apiKey, secret, timestamp, nonce, body, signature);
        rateLimiter.acquire(consumerCode + ':' + serviceCode);
        DataServiceDefinition definition = require(serviceCode);
        if (definition.status() != DataServiceStatus.PUBLISHED) {
            throw new BusinessException("SERVICE-404", "service not published");
        }
        String result = circuitBreaker.call(() -> routeData.get(definition.routeKey()));
        if (result == null) {
            throw new BusinessException("SERVICE-404", "route not found");
        }
        logWriter.write(new ServiceInvokeLog(serviceCode, consumerCode, 200, System.currentTimeMillis() - start, Instant.now()));
        return result;
    }

    public AsyncInvokeLogWriter logWriter() { return logWriter; }
    public SignatureUtil signatureUtil() { return signatureUtil; }

    private DataServiceDefinition require(String serviceCode) {
        DataServiceDefinition definition = services.get(serviceCode);
        if (definition == null) {
            throw new BusinessException("SERVICE-404", "service not found");
        }
        return definition;
    }
}
