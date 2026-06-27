package com.platform.pipeline.catalog;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
    void providesDemoCatalogItemForHttpRegression() {
        CatalogService service = new CatalogService();

        assertFalse(service.query(null, null, null, null).isEmpty());
    }
}
