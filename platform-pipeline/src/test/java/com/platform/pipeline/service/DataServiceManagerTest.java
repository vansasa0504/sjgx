package com.platform.pipeline.service;

import com.platform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
