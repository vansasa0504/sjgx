package com.platform.pipeline.service;

import com.platform.common.exception.BusinessException;
import com.platform.common.model.Page;
import com.platform.common.model.ServiceInvokeLog;
import com.platform.common.security.SignatureUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import com.platform.common.db.IdGenerator;

public class DataServiceManager {
    private final AtomicLong ids = new AtomicLong(1);
    private final Map<String, DataServiceDefinition> services = new ConcurrentHashMap<>();
    private final Map<String, String> routeData = new ConcurrentHashMap<>();
    private final Map<String, String> catalogPartnerGrants = new ConcurrentHashMap<>();
    private final DataServiceStateMachine stateMachine = new DataServiceStateMachine();
    private final AsyncInvokeLogWriter logWriter;
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
        this(apiCredentialRepository, jdbcTemplate, new AsyncInvokeLogWriter(jdbcTemplate));
    }

    public DataServiceManager(ApiCredentialRepository apiCredentialRepository,
                              org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
                              AsyncInvokeLogWriter logWriter) {
        this.apiCredentialRepository = apiCredentialRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = jdbcTemplate != null ? new IdGenerator(jdbcTemplate) : null;
        this.logWriter = logWriter == null ? new AsyncInvokeLogWriter(jdbcTemplate) : logWriter;
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
        if (logWriter.hasRepository()) {
            return logWriter.findByService(serviceCode, consumerId, status, page, size);
        }
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
        return invoke(serviceCode, consumerCode, apiKey, timestamp, nonce, body, signature, null);
    }

    public String invoke(String serviceCode, String consumerCode, String apiKey,
                         long timestamp, String nonce, String body, String signature, String traceId) {
        long start = System.currentTimeMillis();
        String effectiveTraceId = traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
        String effectiveConsumer = consumerCode;
        String requestHash = sha256(body);
        try {
            ApiCredentialRepository.ApiCredential credential = apiCredentialRepository.findByApiKey(apiKey);
            String secret = credential.secret();
            effectiveConsumer = credential.consumerCode();
            if (secret == null || secret.isBlank() || !credential.active()) {
                throw new BusinessException("AUTH-403", "api key disabled");
            }
            if (credential.serviceCode() != null && !credential.serviceCode().isBlank()
                    && !credential.serviceCode().equals(serviceCode)) {
                throw new BusinessException("AUTH-403", "api key service mismatch");
            }
            if (consumerCode != null && !consumerCode.isBlank() && !consumerCode.equals(credential.consumerCode())) {
                throw new BusinessException("AUTH-403", "api key consumer mismatch");
            }
            signatureUtil.verify(apiKey, secret, timestamp, nonce, body, signature);
            rateLimiter.acquire(effectiveConsumer + ':' + serviceCode);
            DataServiceDefinition definition = require(serviceCode);
            if (definition.status() != DataServiceStatus.PUBLISHED) {
                throw new BusinessException("SERVICE-404", "service not published");
            }
            String result = circuitBreaker.call(() -> routeData.get(definition.routeKey()));
            if (result == null) {
                throw new BusinessException("SERVICE-404", "route not found");
            }
            writeInvokeLog(effectiveTraceId, serviceCode, effectiveConsumer, apiKey, requestHash,
                    200, start, ServiceInvokeLog.bytesOf(result), null, null);
            return result;
        } catch (BusinessException ex) {
            writeInvokeLog(effectiveTraceId, serviceCode, effectiveConsumer, apiKey, requestHash,
                    statusFor(ex), start, 0L, ex.code(), sanitize(ex.getMessage()));
            throw ex;
        } catch (RuntimeException ex) {
            writeInvokeLog(effectiveTraceId, serviceCode, effectiveConsumer, apiKey, requestHash,
                    500, start, 0L, "SERVICE-500", sanitize(ex.getMessage()));
            throw ex;
        }
    }

    public ApiCredentialRepository.CreatedCredential createCredential(String serviceCode, String consumerCode) {
        require(serviceCode);
        return apiCredentialRepository.create(consumerCode, serviceCode);
    }

    public List<ApiCredentialRepository.ApiCredential> listCredentials(String serviceCode) {
        require(serviceCode);
        return apiCredentialRepository.list(serviceCode);
    }

    public void grantCatalogPartner(String serviceCode, String consumerCode, String partnerCode) {
        if (serviceCode == null || serviceCode.isBlank()
                || consumerCode == null || consumerCode.isBlank()
                || partnerCode == null || partnerCode.isBlank()) {
            return;
        }
        catalogPartnerGrants.put(serviceCode + ':' + consumerCode, partnerCode);
    }

    public ApiCredentialRepository.CreatedCredential rotateCredential(long id) {
        return apiCredentialRepository.rotate(id);
    }

    public ApiCredentialRepository.ApiCredential disableCredential(long id) {
        return apiCredentialRepository.disable(id);
    }

    public AsyncInvokeLogWriter logWriter() { return logWriter; }
    public SignatureUtil signatureUtil() { return signatureUtil; }
    public ApiCredentialRepository apiCredentialRepository() { return apiCredentialRepository; }

    private void writeInvokeLog(String traceId, String serviceCode, String consumerCode, String apiKey,
                                String requestHash, int status, long start, long responseSize,
                                String errorCode, String errorMessage) {
        String partnerCode = catalogPartnerGrants.get(serviceCode + ':' + consumerCode);
        logWriter.write(new ServiceInvokeLog(traceId, serviceCode, consumerCode, partnerCode, apiKey, requestHash,
                status, System.currentTimeMillis() - start, responseSize, errorCode, errorMessage, Instant.now()));
    }

    private int statusFor(BusinessException ex) {
        String code = ex.code();
        if (code == null) {
            return 400;
        }
        if (code.contains("429")) {
            return 429;
        }
        if (code.startsWith("AUTH-404") || code.startsWith("AUTH-401")) {
            return 401;
        }
        if (code.startsWith("AUTH-403")) {
            return 403;
        }
        if (code.startsWith("SERVICE-404")) {
            return 404;
        }
        if (code.startsWith("SERVICE-503")) {
            return 503;
        }
        return 400;
    }

    private String sanitize(String message) {
        if (message == null) {
            return null;
        }
        return message.replaceAll("(?i)(secret|api[_-]?key|signature)\\s*[:=]\\s*\\S+", "$1=***");
    }

    private String sha256(String body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((body == null ? "" : body).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new BusinessException("AUTH-500", "request hash failed");
        }
    }

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
