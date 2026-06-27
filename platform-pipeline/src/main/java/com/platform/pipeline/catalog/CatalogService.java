package com.platform.pipeline.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class CatalogService {
    private final AtomicLong ids = new AtomicLong(1);
    private final List<DataCatalogItem> items = new ArrayList<>();

    public DataCatalogItem add(String catalogCode, String name, String subject, long partnerId, String dataType,
                               String scenario, List<String> fields, String format, String updateFrequency,
                               String source, String complianceNote, String usageLimit) {
        DataCatalogItem item = new DataCatalogItem(ids.getAndIncrement(), catalogCode, name, subject, partnerId,
                dataType, scenario, List.copyOf(fields), format, updateFrequency, source, complianceNote, usageLimit);
        items.add(item);
        return item;
    }

    public List<DataCatalogItem> query(String subject, Long partnerId, String dataType, String scenario) {
        Stream<DataCatalogItem> stream = items.stream();
        if (subject != null) stream = stream.filter(i -> subject.equals(i.subject()));
        if (partnerId != null) stream = stream.filter(i -> partnerId == i.partnerId());
        if (dataType != null) stream = stream.filter(i -> dataType.equals(i.dataType()));
        if (scenario != null) stream = stream.filter(i -> scenario.equals(i.scenario()));
        return stream.toList();
    }

    public List<DataCatalogItem> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.copyOf(items);
        }
        return items.stream()
                .filter(i -> (i.name() != null && i.name().contains(keyword))
                        || (i.catalogCode() != null && i.catalogCode().contains(keyword))
                        || (i.subject() != null && i.subject().contains(keyword)))
                .toList();
    }

    public DataCatalogItem findById(long id) {
        return items.stream().filter(i -> i.id() == id).findFirst().orElse(null);
    }
}
