package com.platform.pipeline.service;

import com.platform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

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

        String response = manager.invoke("svc-risk", "consumer-a", "api-key", "secret", timestamp, "nonce-a", "{}", signature);

        assertEquals(DataServiceStatus.PUBLISHED, definition.status());
        assertEquals("{\"risk\":\"low\"}", response);
        assertEquals(1, manager.logWriter().logs().size());
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
        manager.invoke("svc-risk", "consumer-a", "api-key", "secret", timestamp, "n1", "{}", sig1);
        manager.invoke("svc-risk", "consumer-a", "api-key", "secret", timestamp, "n2", "{}", sig2);
        assertThrows(BusinessException.class, () -> manager.invoke("svc-risk", "consumer-a", "api-key", "secret", timestamp, "n3", "{}", sig3));
    }
}
