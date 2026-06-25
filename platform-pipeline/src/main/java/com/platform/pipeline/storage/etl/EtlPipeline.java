package com.platform.pipeline.storage.etl;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class EtlPipeline {
    private final AtomicLong ids = new AtomicLong(1);
    private final DataCleaner cleaner = new DataCleaner();
    private final DataStandardizer standardizer = new DataStandardizer();
    private final DataJoiner joiner = new DataJoiner();
    private final DataTagger tagger = new DataTagger();

    public DataAsset process(Map<String, String> raw,
                             Set<String> requiredFields,
                             Map<String, String> fieldMapping,
                             Map<String, String> enrichment) {
        Map<String, String> cleaned = cleaner.clean(raw, requiredFields);
        Map<String, String> standardized = standardizer.standardize(cleaned, fieldMapping);
        Map<String, String> joined = joiner.join(standardized, enrichment);
        return new DataAsset("asset-" + ids.getAndIncrement(), joined, tagger.tag(joined), Instant.now());
    }
}
