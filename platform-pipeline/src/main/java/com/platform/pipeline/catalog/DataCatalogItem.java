package com.platform.pipeline.catalog;

import java.util.List;

public record DataCatalogItem(
        long id,
        String catalogCode,
        String name,
        String subject,
        long partnerId,
        String dataType,
        String scenario,
        List<String> fieldDefinitions,
        String format,
        String updateFrequency,
        String source,
        String complianceNote,
        String usageLimit) {
}
