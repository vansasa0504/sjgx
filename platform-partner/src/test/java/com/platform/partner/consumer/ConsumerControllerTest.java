package com.platform.partner.consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.platform.common.audit.InMemoryAuditLogRepository;
import org.junit.jupiter.api.Test;

class ConsumerControllerTest {
    @Test
    void registersAndListsConsumer() {
        ConsumerService service = new ConsumerService();
        ConsumerController controller = new ConsumerController(service, new InMemoryAuditLogRepository());

        Consumer consumer = controller.register(new ConsumerController.RegisterConsumerRequest(
                "c1", "consumer-a", "risk", "core", "L2")).data();
        assertEquals("c1", consumer.consumerCode());

        assertTrue(controller.list(null, null, null).data().stream().anyMatch(c -> c.id() == consumer.id()));
    }
}
