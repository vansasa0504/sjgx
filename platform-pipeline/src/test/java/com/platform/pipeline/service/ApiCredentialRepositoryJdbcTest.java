package com.platform.pipeline.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.platform.common.exception.BusinessException;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ApiCredentialRepositoryJdbcTest {
    private JdbcTemplate jdbc;
    private ApiCredentialRepository repository;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:api_credential_jdbc;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        jdbc = new JdbcTemplate(ds);
        Flyway.configure().dataSource(ds).locations("filesystem:../db/migration").load().migrate();
        jdbc.update("DELETE FROM t_api_credential");
        repository = new ApiCredentialRepository(jdbc, "jdbc-test-key");
    }

    @Test
    void jdbcStorageDoesNotPersistPlainSecret() {
        ApiCredentialRepository.CreatedCredential created = repository.create("consumer-a", "svc-a");
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT secret, secret_cipher, secret_hash, status
                FROM t_api_credential
                WHERE api_key = ?
                """, created.apiKey());

        assertEquals("__SECRET_CIPHER_ONLY__", row.get("SECRET"));
        assertNotEquals(created.secret(), row.get("SECRET_CIPHER"));
        assertFalse(String.valueOf(row.get("SECRET_CIPHER")).contains(created.secret()));
        assertFalse(String.valueOf(row.get("SECRET_HASH")).contains(created.secret()));
        assertEquals("ACTIVE", row.get("STATUS"));
        assertEquals(created.secret(), repository.findByApiKey(created.apiKey()).secret());
    }

    @Test
    void jdbcRotateSetsRotatedFromDisablesOldKeyAndNewKeyWorks() {
        ApiCredentialRepository.CreatedCredential oldCredential = repository.create("consumer-a", "svc-a");

        ApiCredentialRepository.CreatedCredential newCredential = repository.rotate(oldCredential.id());

        ApiCredentialRepository.ApiCredential oldView = repository.findById(oldCredential.id());
        ApiCredentialRepository.ApiCredential newView = repository.findById(newCredential.id());
        assertEquals("DISABLED", oldView.status());
        assertEquals(oldCredential.id(), newView.rotatedFrom());
        assertThrows(BusinessException.class, () -> repository.findByApiKey(oldCredential.apiKey()));
        assertEquals(newCredential.secret(), repository.findByApiKey(newCredential.apiKey()).secret());
    }

    @Test
    void jdbcDisableRejectsApiKeyAndListFiltersByServiceCode() {
        ApiCredentialRepository.CreatedCredential svcA = repository.create("consumer-a", "svc-a");
        repository.create("consumer-b", "svc-b");

        List<ApiCredentialRepository.ApiCredential> svcAList = repository.list("svc-a");
        assertEquals(1, svcAList.size());
        assertEquals(svcA.apiKey(), svcAList.get(0).apiKey());

        repository.disable(svcA.id());

        assertEquals("DISABLED", repository.findById(svcA.id()).status());
        assertThrows(BusinessException.class, () -> repository.findByApiKey(svcA.apiKey()));
    }

    @Test
    void jdbcHashMismatchRejectsCredentialWithoutLeakingSecret() {
        ApiCredentialRepository.CreatedCredential created = repository.create("consumer-a", "svc-a");
        jdbc.update("UPDATE t_api_credential SET secret_hash = ? WHERE api_key = ?", "bad-hash", created.apiKey());

        BusinessException ex = assertThrows(BusinessException.class, () -> repository.findByApiKey(created.apiKey()));

        assertFalse(ex.getMessage().contains(created.secret()));
    }

    @Test
    void invokeResponseLogAndExceptionDoNotContainPlainSecret() {
        DataServiceManager manager = new DataServiceManager(repository);
        manager.register("svc-a", "Service A", "route-a");
        manager.apply("svc-a", DataServiceEvent.DEFINE);
        manager.apply("svc-a", DataServiceEvent.TEST);
        manager.apply("svc-a", DataServiceEvent.PUBLISH);
        ApiCredentialRepository.CreatedCredential created = manager.createCredential("svc-a", "consumer-a");
        long timestamp = java.time.Instant.now().getEpochSecond();
        String signature = manager.signatureUtil().sign(created.apiKey(), created.secret(), timestamp, "safe-nonce", "{}");

        String response = manager.invoke("svc-a", null, created.apiKey(), timestamp, "safe-nonce", "{}", signature);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> manager.invoke("svc-a", null, created.apiKey(), timestamp, "bad-nonce", "{}", "bad"));

        assertFalse(response.contains(created.secret()));
        assertTrue(manager.logWriter().logs().stream()
                .noneMatch(log -> String.valueOf(log).contains(created.secret())));
        assertFalse(ex.getMessage().contains(created.secret()));
    }
}
