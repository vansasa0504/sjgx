package com.platform.pipeline.service;

import com.platform.common.exception.BusinessException;
import com.platform.common.model.ServiceInvokeLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataServiceManagerTest {
    @Test
    void publishesInvokesAndWritesLog() {
        DataServiceManager manager = new DataServiceManager();
        DataServiceDefinition definition = manager.register("svc-risk", "风险数据", "route-risk");
        manager.apply("svc-risk", DataServiceEvent.DEFINE);
        manager.apply("svc-risk", DataServiceEvent.TEST);
        manager.apply("svc-risk", DataServiceEvent.PUBLISH);
        manager.putRouteData("route-risk", "{\"risk\":\"low\"}");
        long timestamp = Instant.now().getEpochSecond();
        String signature = manager.signatureUtil().sign("api-key", "secret", timestamp, "nonce-a", "{}");

        String response = manager.invoke("svc-risk", "consumer-a", "api-key", timestamp, "nonce-a", "{}", signature);

        assertEquals(DataServiceStatus.PUBLISHED, definition.status());
        assertEquals("{\"risk\":\"low\"}", response);
        assertEquals(1, manager.logWriter().logs().size());
        ServiceInvokeLog log = manager.logWriter().logs().get(0);
        assertNotNull(log.traceId());
        assertNotNull(log.requestHash());
        assertEquals(200, log.status());
        assertEquals(ServiceInvokeLog.bytesOf(response), log.responseSize());
    }

    @Test
    void approvedCatalogGrantFillsPartnerCodeInInvokeLog() {
        DataServiceManager manager = new DataServiceManager(new ApiCredentialRepository(null, "unit-test-key"));
        manager.register("svc-partner", "合作方服务", "route-partner");
        manager.apply("svc-partner", DataServiceEvent.DEFINE);
        manager.apply("svc-partner", DataServiceEvent.TEST);
        manager.apply("svc-partner", DataServiceEvent.PUBLISH);
        manager.grantCatalogPartner("svc-partner", "consumer-a", "partner-1");
        ApiCredentialRepository.CreatedCredential credential = manager.createCredential("svc-partner", "consumer-a");
        long timestamp = Instant.now().getEpochSecond();
        String signature = manager.signatureUtil().sign(credential.apiKey(), credential.secret(), timestamp, "partner-nonce", "{}");

        manager.invoke("svc-partner", null, credential.apiKey(), timestamp, "partner-nonce", "{}", signature);

        assertEquals("partner-1", manager.logWriter().logs().get(0).partnerCode());
    }

    @Test
    void requestHashIsConsistentBetweenSuccessAndFailureForSameBody() {
        DataServiceManager manager = new DataServiceManager();
        manager.register("svc-hash", "hash service", "route-hash");
        manager.apply("svc-hash", DataServiceEvent.DEFINE);
        manager.apply("svc-hash", DataServiceEvent.TEST);
        manager.apply("svc-hash", DataServiceEvent.PUBLISH);
        manager.putRouteData("route-hash", "{\"ok\":true}");
        long timestamp = Instant.now().getEpochSecond();
        String body = "{\"q\":\"same-body\"}";
        String okSignature = manager.signatureUtil().sign("api-key", "secret", timestamp, "nonce-ok", body);
        manager.invoke("svc-hash", "consumer-a", "api-key", timestamp, "nonce-ok", body, okSignature);
        assertThrows(BusinessException.class, () -> manager.invoke("svc-hash", "consumer-a", "api-key",
                timestamp, "nonce-fail", body, "bad-signature"));

        List<ServiceInvokeLog> logs = manager.logWriter().logs();
        ServiceInvokeLog successLog = logs.stream().filter(l -> l.status() == 200).findFirst().orElseThrow();
        ServiceInvokeLog failureLog = logs.stream().filter(l -> l.status() != 200).findFirst().orElseThrow();
        assertEquals(successLog.requestHash(), failureLog.requestHash(),
                "request_hash must be identical for the same body regardless of success/failure");
    }

    @Test
    void writesFailedInvokeLogWithTraceIdAndSanitizedError() {
        DataServiceManager manager = new DataServiceManager();
        manager.register("svc-risk", "风险数据", "route-risk");
        manager.apply("svc-risk", DataServiceEvent.DEFINE);
        manager.apply("svc-risk", DataServiceEvent.TEST);
        manager.apply("svc-risk", DataServiceEvent.PUBLISH);
        long timestamp = Instant.now().getEpochSecond();

        assertThrows(BusinessException.class, () -> manager.invoke("svc-risk", "consumer-a", "api-key",
                timestamp, "nonce-fail", "{}", "bad-signature", "trace-test-1"));

        ServiceInvokeLog log = manager.logWriter().logs().get(0);
        assertEquals("trace-test-1", log.traceId());
        assertEquals("consumer-a", log.consumerCode());
        assertEquals("AUTH-403", log.errorCode());
        assertEquals(403, log.status());
        assertNotNull(log.requestHash());
    }

    @Test
    void registeredServiceHasDefaultRouteDataForHttpRegression() {
        DataServiceManager manager = new DataServiceManager();
        manager.register("svc-demo", "演示服务", "route-demo");
        manager.apply("svc-demo", DataServiceEvent.DEFINE);
        manager.apply("svc-demo", DataServiceEvent.TEST);
        manager.apply("svc-demo", DataServiceEvent.PUBLISH);
        long timestamp = Instant.now().getEpochSecond();
        String signature = manager.signatureUtil().sign("api-key", "secret", timestamp, "nonce-default", "{}");

        String response = manager.invoke("svc-demo", "consumer-a", "api-key", timestamp, "nonce-default", "{}", signature);

        assertEquals("{\"status\":\"ok\"}", response);
    }

    @Test
    void storesCredentialSecretEncryptedAndUsesServerSideLookup() {
        ApiCredentialRepository repository = new ApiCredentialRepository(null, "unit-test-key");
        ApiCredentialRepository.CreatedCredential credential = repository.create("consumer-a", "svc-secure");
        ApiCredentialRepository.StoredSecretSnapshot stored = repository.storedSecretSnapshot(credential.apiKey());

        assertNotEquals(credential.secret(), stored.secretCipher());
        assertFalse(stored.secretCipher().contains(credential.secret()));
        assertFalse(stored.secretHash().contains(credential.secret()));
        assertEquals(credential.secret(), repository.findByApiKey(credential.apiKey()).secret());
    }

    @Test
    void productionProfileRequiresConfiguredCredentialSm4Key() {
        Assumptions.assumeTrue(System.getenv("API_CREDENTIAL_SM4_KEY") == null || System.getenv("API_CREDENTIAL_SM4_KEY").isBlank());
        String previous = System.getProperty("spring.profiles.active");
        System.setProperty("spring.profiles.active", "prod");
        try {
            assertThrows(IllegalStateException.class, ApiCredentialRepository::new);
        } finally {
            if (previous == null) {
                System.clearProperty("spring.profiles.active");
            } else {
                System.setProperty("spring.profiles.active", previous);
            }
        }
    }

    @Test
    void verifiesSignatureWithCredentialRejectsBadExpiredAndReplayRequests() {
        DataServiceManager manager = new DataServiceManager(new ApiCredentialRepository(null, "unit-test-key"));
        manager.register("svc-secure", "安全服务", "route-secure");
        manager.apply("svc-secure", DataServiceEvent.DEFINE);
        manager.apply("svc-secure", DataServiceEvent.TEST);
        manager.apply("svc-secure", DataServiceEvent.PUBLISH);
        ApiCredentialRepository.CreatedCredential credential = manager.createCredential("svc-secure", "consumer-a");
        long timestamp = Instant.now().getEpochSecond();
        String signature = manager.signatureUtil().sign(credential.apiKey(), credential.secret(), timestamp, "nonce-ok", "{}");

        assertEquals("{\"status\":\"ok\"}", manager.invoke("svc-secure", null, credential.apiKey(), timestamp, "nonce-ok", "{}", signature));
        assertThrows(BusinessException.class, () -> manager.invoke("svc-secure", null, credential.apiKey(), timestamp, "nonce-ok", "{}", signature));
        assertThrows(BusinessException.class, () -> manager.invoke("svc-secure", null, credential.apiKey(), timestamp, "nonce-bad", "{}", "bad"));
        String expired = manager.signatureUtil().sign(credential.apiKey(), credential.secret(), timestamp - 1_000, "nonce-expired", "{}");
        assertThrows(BusinessException.class, () -> manager.invoke("svc-secure", null, credential.apiKey(), timestamp - 1_000, "nonce-expired", "{}", expired));
    }

    @Test
    void rotationDisablesOldKeyAndDisableRejectsCurrentKey() {
        DataServiceManager manager = new DataServiceManager(new ApiCredentialRepository(null, "unit-test-key"));
        manager.register("svc-secure", "安全服务", "route-secure");
        manager.apply("svc-secure", DataServiceEvent.DEFINE);
        manager.apply("svc-secure", DataServiceEvent.TEST);
        manager.apply("svc-secure", DataServiceEvent.PUBLISH);
        ApiCredentialRepository.CreatedCredential oldCredential = manager.createCredential("svc-secure", "consumer-a");
        ApiCredentialRepository.CreatedCredential newCredential = manager.rotateCredential(oldCredential.id());
        long timestamp = Instant.now().getEpochSecond();
        String oldSignature = manager.signatureUtil().sign(oldCredential.apiKey(), oldCredential.secret(), timestamp, "old-nonce", "{}");
        String newSignature = manager.signatureUtil().sign(newCredential.apiKey(), newCredential.secret(), timestamp, "new-nonce", "{}");

        assertThrows(BusinessException.class, () -> manager.invoke("svc-secure", null, oldCredential.apiKey(), timestamp, "old-nonce", "{}", oldSignature));
        assertEquals("{\"status\":\"ok\"}", manager.invoke("svc-secure", null, newCredential.apiKey(), timestamp, "new-nonce", "{}", newSignature));

        manager.disableCredential(newCredential.id());
        String disabledSignature = manager.signatureUtil().sign(newCredential.apiKey(), newCredential.secret(), timestamp, "disabled-nonce", "{}");
        assertThrows(BusinessException.class, () -> manager.invoke("svc-secure", null, newCredential.apiKey(), timestamp, "disabled-nonce", "{}", disabledSignature));
    }

    @Test
    void rejectsIllegalLifecycleRateLimitAndBreakerFailures() {
        DataServiceManager manager = new DataServiceManager();
        manager.register("svc-risk", "风险数据", "route-risk");
        assertThrows(BusinessException.class, () -> manager.apply("svc-risk", DataServiceEvent.PUBLISH));

        manager.apply("svc-risk", DataServiceEvent.DEFINE);
        manager.apply("svc-risk", DataServiceEvent.TEST);
        manager.apply("svc-risk", DataServiceEvent.PUBLISH);
        manager.putRouteData("route-risk", "ok");
        long timestamp = Instant.now().getEpochSecond();
        String sig1 = manager.signatureUtil().sign("api-key", "secret", timestamp, "n1", "{}");
        String sig2 = manager.signatureUtil().sign("api-key", "secret", timestamp, "n2", "{}");
        String sig3 = manager.signatureUtil().sign("api-key", "secret", timestamp, "n3", "{}");
        manager.invoke("svc-risk", "consumer-a", "api-key", timestamp, "n1", "{}", sig1);
        manager.invoke("svc-risk", "consumer-a", "api-key", timestamp, "n2", "{}", sig2);
        assertThrows(BusinessException.class, () -> manager.invoke("svc-risk", "consumer-a", "api-key", timestamp, "n3", "{}", sig3));
    }

    @Test
    void rateLimiterReleasesCapacityAfterWindow() {
        MutableClock clock = new MutableClock();
        RateLimiter limiter = new RateLimiter(2, 1_000, clock);

        limiter.acquire("svc-a");
        limiter.acquire("svc-a");
        assertThrows(BusinessException.class, () -> limiter.acquire("svc-a"));

        clock.advanceMillis(1_000);
        limiter.acquire("svc-a");
    }

    @Test
    void circuitBreakerOpensAndRecoversThroughHalfOpenState() {
        MutableClock clock = new MutableClock();
        CircuitBreaker breaker = new CircuitBreaker(2, 1_000, clock);

        assertThrows(BusinessException.class, () -> breaker.call(() -> { throw new IllegalStateException("down"); }));
        assertEquals(CircuitBreaker.State.CLOSED, breaker.state());
        assertThrows(BusinessException.class, () -> breaker.call(() -> { throw new IllegalStateException("down"); }));
        assertEquals(CircuitBreaker.State.OPEN, breaker.state());
        assertThrows(BusinessException.class, () -> breaker.call(() -> "blocked"));

        clock.advanceMillis(1_000);
        assertEquals("ok", breaker.call(() -> "ok"));
        assertEquals(CircuitBreaker.State.CLOSED, breaker.state());
    }

    private static class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-06-25T00:00:00Z");

        void advanceMillis(long millis) {
            instant = instant.plusMillis(millis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
