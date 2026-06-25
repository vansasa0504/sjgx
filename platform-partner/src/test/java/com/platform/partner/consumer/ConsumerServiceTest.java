package com.platform.partner.consumer;

import com.platform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumerServiceTest {
    @Test
    void managesLifecycleAndBlocksQuotaExhaustion() {
        ConsumerService service = new ConsumerService();
        Consumer consumer = service.register("risk-core", "风控核心", "风险", "CORE", "L2");

        service.apply(consumer.id(), ConsumerEvent.SUBMIT);
        service.apply(consumer.id(), ConsumerEvent.APPROVE);
        service.configureQuota(consumer.id(), 2, 2);
        service.apply(consumer.id(), ConsumerEvent.ENABLE);
        service.consume(consumer.id());
        service.consume(consumer.id());

        assertEquals(ConsumerStatus.ENABLED, consumer.status());
        assertThrows(BusinessException.class, () -> service.consume(consumer.id()));
        assertTrue(service.events().stream().anyMatch(e -> e.contains("QUOTA_WARNING")));
        assertTrue(service.events().stream().anyMatch(e -> e.contains("QUOTA_EXCEEDED")));
    }

    @Test
    void rejectsIllegalLifecycleMove() {
        ConsumerService service = new ConsumerService();
        Consumer consumer = service.register("crm", "CRM", "营销", "APP", "L1");

        assertThrows(BusinessException.class, () -> service.apply(consumer.id(), ConsumerEvent.ENABLE));
    }
}
