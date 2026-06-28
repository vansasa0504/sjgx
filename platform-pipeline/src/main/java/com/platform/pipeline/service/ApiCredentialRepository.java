package com.platform.pipeline.service;

import com.platform.common.db.IdGenerator;
import com.platform.common.exception.BusinessException;
import com.platform.common.security.Sm4Util;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * API Key credential repository. Secrets are encrypted at rest and returned in plain text only when created or rotated.
 */
public class ApiCredentialRepository {
    private static final String ACTIVE = "ACTIVE";
    private static final String DISABLED = "DISABLED";
    private static final String SECRET_PLACEHOLDER = "__SECRET_CIPHER_ONLY__";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, StoredCredential> credentials = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong(1);
    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;
    private final String sm4Key;

    public ApiCredentialRepository() {
        this(null, secretKey());
    }

    public ApiCredentialRepository(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, secretKey());
    }

    public ApiCredentialRepository(JdbcTemplate jdbcTemplate, String sm4Key) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = jdbcTemplate == null ? null : new IdGenerator(jdbcTemplate);
        this.sm4Key = sm4Key;
        if (!useDb()) {
            create("consumer-a", null, "api-key", "secret");
        }
    }

    public CreatedCredential create(String consumerCode, String serviceCode) {
        return create(consumerCode, serviceCode, randomToken("ak"), randomToken("sk"), null);
    }

    CreatedCredential create(String consumerCode, String serviceCode, String apiKey, String secret) {
        return create(consumerCode, serviceCode, apiKey, secret, null);
    }

    CreatedCredential create(String consumerCode, String serviceCode, String apiKey, String secret, Long rotatedFrom) {
        requireText(consumerCode, "consumer code required");
        requireText(apiKey, "api key required");
        requireText(secret, "secret required");
        long id = nextId();
        StoredCredential stored = store(new StoredCredential(
                id, apiKey, encrypt(secret), hash(secret), consumerCode, serviceCode, ACTIVE, rotatedFrom, Instant.now(), Instant.now()));
        return new CreatedCredential(stored.id(), stored.apiKey(), secret, stored.consumerCode(), stored.serviceCode(), stored.status());
    }

    public CreatedCredential rotate(long id) {
        ApiCredential current = findById(id);
        disable(id);
        return create(current.consumerCode(), current.serviceCode(), randomToken("ak"), randomToken("sk"), current.id());
    }

    public ApiCredential disable(long id) {
        ApiCredential current = findById(id);
        if (useDb()) {
            jdbcTemplate.update("UPDATE t_api_credential SET enabled = 0, status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    DISABLED, id);
        } else {
            StoredCredential stored = credentials.get(current.apiKey());
            credentials.put(current.apiKey(), stored.withStatus(DISABLED));
        }
        return findById(id);
    }

    public ApiCredential findByApiKey(String apiKey) {
        StoredCredential credential = storedByApiKey(apiKey);
        if (!ACTIVE.equals(credential.status())) {
            throw new BusinessException("AUTH-403", "api key disabled");
        }
        return credential.toApiCredential(decryptAndVerify(credential));
    }

    public ApiCredential findById(long id) {
        StoredCredential credential;
        if (useDb()) {
            credential = jdbcTemplate.queryForObject(
                    "SELECT id, api_key, secret_cipher, secret_hash, consumer_code, service_code, enabled, status, rotated_from, created_at, updated_at FROM t_api_credential WHERE id = ?",
                    (rs, rowNum) -> new StoredCredential(
                            rs.getLong("id"), rs.getString("api_key"), rs.getString("secret_cipher"),
                            rs.getString("secret_hash"), rs.getString("consumer_code"), rs.getString("service_code"),
                            normalizeStatus(rs.getString("status"), rs.getInt("enabled")),
                            rs.getObject("rotated_from") == null ? null : rs.getLong("rotated_from"),
                            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()),
                    id);
        } else {
            credential = credentials.values().stream()
                    .filter(stored -> stored.id() == id)
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("AUTH-404", "api credential not found"));
        }
        return credential.toApiCredential(null);
    }

    public List<ApiCredential> list(String serviceCode) {
        if (useDb()) {
            String sql = serviceCode == null || serviceCode.isBlank()
                    ? "SELECT id, api_key, secret_cipher, secret_hash, consumer_code, service_code, enabled, status, rotated_from, created_at, updated_at FROM t_api_credential ORDER BY id"
                    : "SELECT id, api_key, secret_cipher, secret_hash, consumer_code, service_code, enabled, status, rotated_from, created_at, updated_at FROM t_api_credential WHERE service_code = ? ORDER BY id";
            Object[] args = serviceCode == null || serviceCode.isBlank() ? new Object[0] : new Object[]{serviceCode};
            return jdbcTemplate.query(sql, (rs, rowNum) -> new StoredCredential(
                    rs.getLong("id"), rs.getString("api_key"), rs.getString("secret_cipher"),
                    rs.getString("secret_hash"), rs.getString("consumer_code"), rs.getString("service_code"),
                    normalizeStatus(rs.getString("status"), rs.getInt("enabled")),
                    rs.getObject("rotated_from") == null ? null : rs.getLong("rotated_from"),
                    rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant())
                    .toApiCredential(null), args);
        }
        return credentials.values().stream()
                .filter(stored -> serviceCode == null || serviceCode.isBlank() || serviceCode.equals(stored.serviceCode()))
                .map(stored -> stored.toApiCredential(null))
                .toList();
    }

    StoredSecretSnapshot storedSecretSnapshot(String apiKey) {
        StoredCredential stored = storedByApiKey(apiKey);
        return new StoredSecretSnapshot(stored.secretCipher(), stored.secretHash());
    }

    private StoredCredential store(StoredCredential credential) {
        if (useDb()) {
            jdbcTemplate.update("""
                    INSERT INTO t_api_credential
                        (id, api_key, secret, secret_cipher, secret_hash, consumer_code, service_code, enabled, status, rotated_from, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    credential.id(), credential.apiKey(), SECRET_PLACEHOLDER, credential.secretCipher(),
                    credential.secretHash(), credential.consumerCode(), credential.serviceCode(), 1,
                    credential.status(), credential.rotatedFrom());
        } else {
            credentials.put(credential.apiKey(), credential);
        }
        return credential;
    }

    private StoredCredential storedByApiKey(String apiKey) {
        requireText(apiKey, "api key required");
        if (useDb()) {
            try {
                return jdbcTemplate.queryForObject(
                        "SELECT id, api_key, secret_cipher, secret_hash, consumer_code, service_code, enabled, status, rotated_from, created_at, updated_at FROM t_api_credential WHERE api_key = ?",
                        (rs, rowNum) -> new StoredCredential(
                                rs.getLong("id"), rs.getString("api_key"), rs.getString("secret_cipher"),
                                rs.getString("secret_hash"), rs.getString("consumer_code"), rs.getString("service_code"),
                                normalizeStatus(rs.getString("status"), rs.getInt("enabled")),
                                rs.getObject("rotated_from") == null ? null : rs.getLong("rotated_from"),
                                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()),
                        apiKey);
            } catch (EmptyResultDataAccessException ex) {
                throw new BusinessException("AUTH-404", "api key not found");
            }
        }
        StoredCredential credential = credentials.get(apiKey);
        if (credential == null) {
            throw new BusinessException("AUTH-404", "api key not found");
        }
        return credential;
    }

    private long nextId() {
        return useDb() ? idGenerator.nextId("t_api_credential") : ids.getAndIncrement();
    }

    private boolean useDb() {
        return jdbcTemplate != null;
    }

    private String encrypt(String secret) {
        return Sm4Util.encrypt(secret, sm4Key);
    }

    private String decrypt(String cipher) {
        return Sm4Util.decrypt(cipher, sm4Key);
    }

    private String decryptAndVerify(StoredCredential credential) {
        if (credential.secretCipher() == null || credential.secretCipher().isBlank()) {
            throw new BusinessException("AUTH-500", "api credential secret missing");
        }
        String secret = decrypt(credential.secretCipher());
        if (!hash(secret).equals(credential.secretHash())) {
            throw new BusinessException("AUTH-500", "api credential secret corrupted");
        }
        return secret;
    }

    private static String hash(String secret) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new BusinessException("AUTH-500", "secret hash failed");
        }
    }

    private static String randomToken(String prefix) {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return prefix + "_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String secretKey() {
        String key = System.getenv("API_CREDENTIAL_SM4_KEY");
        if (key != null && !key.isBlank()) {
            return key;
        }
        if (isProductionProfile()) {
            throw new IllegalStateException("API_CREDENTIAL_SM4_KEY must be set in production profile");
        }
        return "local-api-credential-key";
    }

    private static boolean isProductionProfile() {
        String active = System.getProperty("spring.profiles.active", System.getenv().getOrDefault("SPRING_PROFILES_ACTIVE", ""));
        String normalized = active.toLowerCase();
        return normalized.contains("prod") || normalized.contains("production");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("AUTH-400", message);
        }
    }

    private static String normalizeStatus(String status, int enabled) {
        if (status == null || status.isBlank()) {
            return enabled == 1 ? ACTIVE : DISABLED;
        }
        return status;
    }

    public record ApiCredential(long id, String apiKey, String secret, String consumerCode,
                                String serviceCode, String status, Long rotatedFrom) {
        public boolean active() {
            return ACTIVE.equals(status);
        }
    }

    public record CreatedCredential(long id, String apiKey, String secret, String consumerCode,
                                    String serviceCode, String status) {
    }

    public record StoredSecretSnapshot(String secretCipher, String secretHash) {
    }

    private record StoredCredential(long id, String apiKey, String secretCipher, String secretHash,
                                    String consumerCode, String serviceCode, String status, Long rotatedFrom,
                                    Instant createdAt, Instant updatedAt) {
        ApiCredential toApiCredential(String plainSecret) {
            return new ApiCredential(id, apiKey, plainSecret, consumerCode, serviceCode, status, rotatedFrom);
        }

        StoredCredential withStatus(String nextStatus) {
            return new StoredCredential(id, apiKey, secretCipher, secretHash, consumerCode, serviceCode,
                    nextStatus, rotatedFrom, createdAt, Instant.now());
        }
    }
}
