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
import com.platform.common.db.IdGenerator;

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
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;

    public DataServiceManager() {
        this(new ApiCredentialRepository(), null);
    }

    public DataServiceManager(ApiCredentialRepository apiCredentialRepository) {
        this(apiCredentialRepository, null);
    }

    public DataServiceManager(ApiCredentialRepository apiCredentialRepository,
                              org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.apiCredentialRepository = apiCredentialRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = jdbcTemplate != null ? new IdGenerator(jdbcTemplate) : null;
    }


    private boolean useDb() {
        return jdbcTemplate != null;
    }

    public DataServiceDefinition register(String serviceCode, String name, String routeKey) {
        long id = useDb() ? idGenerator.nextId("t_data_service") : ids.getAndIncrement();
        DataServiceDefinition definition = new DataServiceDefinition(id, serviceCode, name, routeKey);
        if (useDb()) {
            jdbcTemplate.update(
                    "INSERT INTO t_data_service (id, service_code, name, route_key, version_no, status, created_at) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                    id, serviceCode, name, routeKey, definition.version(), definition.status().name());
        }
        services.put(serviceCode, definition);
        routeData.putIfAbsent(routeKey, "{\"status\":\"ok\"}");
        return definition;
    }



    public List<DataServiceDefinition> list(String keyword, String status) {
        if (useDb()) {
            return jdbcTemplate.query(
                    "SELECT id, service_code, name, route_key, version_no, status FROM t_data_service ORDER BY id",
                    (rs, rowNum) -> new DataServiceDefinition(
                            rs.getLong("id"), rs.getString("service_code"), rs.getString("name"),
                            rs.getString("route_key"))).stream()
                    
                    .filter(d -> keyword == null || keyword.isBlank()
                            || (d.serviceCode() != null && d.serviceCode().contains(keyword))
                            || (d.name() != null && d.name().contains(keyword)))
                    .filter(d -> status == null || status.isBlank() || status.equals(d.status().name()))
                    .toList();
        }
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
        if (useDb()) {
            jdbcTemplate.update("UPDATE t_data_service SET name = ?, route_key = ?, updated_at = CURRENT_TIMESTAMP WHERE service_code = ?",
                    definition.name(), definition.routeKey(), serviceCode);
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
        if (useDb()) {
            jdbcTemplate.update("UPDATE t_data_service SET status = ?, version_no = ?, updated_at = CURRENT_TIMESTAMP WHERE service_code = ?",
                    definition.status().name(), definition.version(), serviceCode);
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
        if (definition == null && useDb()) {
            try {
                definition = jdbcTemplate.queryForObject(
                        "SELECT id, service_code, name, route_key, version_no, status FROM t_data_service WHERE service_code = ?",
                        (rs, rowNum) -> {
                            DataServiceDefinition d = new DataServiceDefinition(
                                    rs.getLong("id"), rs.getString("service_code"), rs.getString("name"),
                                    rs.getString("route_key"));
                            d.restoreVersion(rs.getInt("version_no"));
                            d.status(DataServiceStatus.valueOf(rs.getString("status")));
                            return d;
                        },
                        serviceCode);
                if (definition != null) {
                    services.put(serviceCode, definition);
                    routeData.putIfAbsent(definition.routeKey(), "{\"status\":\"ok\"}");
                }
            } catch (Exception ex) {
                // fall through
            }
        }
        if (definition == null) {
            throw new BusinessException("SERVICE-404", "service not found");
        }
        return definition;
    }
}
