package com.platform.pipeline.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CatalogControllerTest {
    @Test
    void listsAndAppliesCatalog() {
        CatalogService service = new CatalogService();
        service.add("cat-1", "征信数据", "risk", 1L, "CREDIT", "review", java.util.List.of("id", "name"), "JSON", "DAILY", "征信联合", "L2", "内部");
        CatalogController controller = new CatalogController(service);

        assertEquals(1, controller.list("risk", null, null, null).data().size());
        assertTrue(controller.search("征信").data().stream().anyMatch(i -> "cat-1".equals(i.catalogCode())));

        var application = controller.apply(1L, new CatalogController.ApplyRequest("风控", "read")).data();
        assertEquals("PENDING", application.status());
        assertEquals("APPROVED", controller.approve(application.id()).data().status());
    }
}
