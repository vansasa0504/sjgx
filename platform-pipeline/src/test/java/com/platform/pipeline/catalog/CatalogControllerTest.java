package com.platform.pipeline.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.platform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class CatalogControllerTest {
    @Test
    void listsAndAppliesCatalog() {
        CatalogService service = new CatalogService();
        service.add("cat-1", "征信数据", "risk", 1L, "CREDIT", "review", java.util.List.of("id", "name"), "JSON", "DAILY", "征信联合", "L2", "内部");
        InMemoryCatalogApplicationRepository repository = new InMemoryCatalogApplicationRepository();
        CatalogController controller = new CatalogController(service, repository, null, null);

        assertEquals(1, controller.list("risk", null, null, null).data().size());
        assertTrue(controller.search("征信").data().stream().anyMatch(i -> "cat-1".equals(i.catalogCode())));

        var application = controller.apply(1L, new CatalogController.ApplyRequest("风控", "read")).data();
        assertEquals("PENDING", application.status());
        assertEquals("APPROVED", controller.approve(application.id()).data().status());
    }

    @Test
    void applicationCanOnlyMoveFromPendingOnce() {
        InMemoryCatalogApplicationRepository repository = new InMemoryCatalogApplicationRepository();
        CatalogApplication application = repository.create(1L, "applicant-a", "reason", "svc-a");

        assertEquals("APPROVED", repository.approve(application.id(), "reviewer").status());
        org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> repository.reject(application.id(), "reviewer"));
    }

    @Test
    void rejectsPendingApplication() {
        CatalogService service = new CatalogService();
        service.add("cat-1", "征信数据", "risk", 1L, "CREDIT", "review", java.util.List.of("id", "name"), "JSON", "DAILY", "征信联合", "L2", "内部");
        InMemoryCatalogApplicationRepository repository = new InMemoryCatalogApplicationRepository();
        CatalogController controller = new CatalogController(service, repository, null, null);
        CatalogApplication application = repository.create(1L, "applicant-a", "reason", "svc-a");

        CatalogApplication rejected = controller.reject(application.id()).data();

        assertEquals("REJECTED", rejected.status());
        org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> repository.reject(application.id(), "reviewer"));
    }
}
