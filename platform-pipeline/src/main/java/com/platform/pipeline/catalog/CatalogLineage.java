package com.platform.pipeline.catalog;

public record CatalogLineage(long catalogId, String nodeType, long nodeId, String nodeName, String direction) {
    public static final String PARTNER = "PARTNER";
    public static final String INGEST_TASK = "INGEST_TASK";
    public static final String DATA_SERVICE = "DATA_SERVICE";
    public static final String UPSTREAM = "UPSTREAM";
    public static final String DOWNSTREAM = "DOWNSTREAM";
}
