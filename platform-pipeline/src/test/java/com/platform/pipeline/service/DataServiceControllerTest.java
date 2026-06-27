package com.platform.pipeline.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DataServiceControllerTest {
    @Test
    void registersAndListsService() {
        DataServiceManager manager = new DataServiceManager();
        DataServiceController controller = new DataServiceController(manager);

        controller.register(new DataServiceController.RegisterServiceRequest("svc-risk", "Risk Service", "risk-route"));
        assertTrue(controller.list(null, null).data().stream().anyMatch(d -> "svc-risk".equals(d.serviceCode())));
        assertEquals("Risk Service", controller.detail("svc-risk").data().name());
    }
}
