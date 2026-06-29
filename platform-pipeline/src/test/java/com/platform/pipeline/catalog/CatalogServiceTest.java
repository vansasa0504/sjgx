package com.platform.pipeline.catalog;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogServiceTest {
    @Test
    void queriesCatalogByMultipleDimensions() {
        CatalogService service = new CatalogService();
        service.add("cat-credit", "征信企业画像", "信用", 1L, "征信", "风控", List.of("name", "score"), "JSON", "daily", "征信联合服务", "L2", "授权可见");
        service.add("cat-gov", "政务企业状态", "政务", 2L, "政务", "营销", List.of("name", "status"), "XML", "hourly", "政务数据中心", "L1", "内部使用");

        assertEquals(1, service.query("信用", null, null, null).size());
        assertEquals(1, service.query(null, 2L, "政务", "营销").size());
        assertEquals(0, service.query("信用", 2L, null, null).size());
    }

    @Test
    void productionConstructorDoesNotSeedCatalog() {
        CatalogService service = new CatalogService();

        assertTrue(service.query(null, null, null, null).isEmpty());
    }

    @Test
    void previewMasksSensitiveFieldsAndReturnsStats() {
        CatalogService service = new CatalogService();
        DataCatalogItem item = service.add("cat-demo", "示例资产", "征信", 1L, "JSON", "风控",
                List.of("name", "idCard", "credential"), "JSON", "DAILY", "DEMO", "L2", "内部");

        CatalogController.PreviewResult preview = service.preview(item);

        assertEquals(1, preview.sample().size());
        assertEquals("***MASKED***", preview.sample().get(0).get("idCard"));
        assertEquals("***MASKED***", preview.sample().get(0).get("credential"));
        assertEquals(3, preview.stats().get("fieldCount"));
        assertFalse(preview.qualityReport().isBlank());
    }
}
