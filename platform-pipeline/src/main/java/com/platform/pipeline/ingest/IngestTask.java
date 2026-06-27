package com.platform.pipeline.ingest;

import java.net.URI;
import java.util.List;
import java.util.Map;

public class IngestTask {
    private final long id;
    private final long partnerId;
    private final URI endpoint;
    private final String protocol;
    private final String format;
    private String syncMode;
    private String cronExpression;
    private Map<String, String> fieldMapping = Map.of();
    private List<String> qualityRules = List.of();
    private IngestTaskStatus status = IngestTaskStatus.DRAFT;

    public IngestTask(long id, long partnerId, URI endpoint, String protocol, String format) {
        this.id = id;
        this.partnerId = partnerId;
        this.endpoint = endpoint;
        this.protocol = protocol;
        this.format = format;
    }

    public long id() {
        return id;
    }

    public long partnerId() {
        return partnerId;
    }

    public URI endpoint() {
        return endpoint;
    }

    public String protocol() {
        return protocol;
    }

    public String format() {
        return format;
    }

    public String syncMode() {
        return syncMode;
    }

    public void syncMode(String syncMode) {
        this.syncMode = syncMode;
    }

    public String cronExpression() {
        return cronExpression;
    }

    public void cronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public Map<String, String> fieldMapping() {
        return fieldMapping;
    }

    public void fieldMapping(Map<String, String> fieldMapping) {
        this.fieldMapping = fieldMapping == null ? Map.of() : Map.copyOf(fieldMapping);
    }

    public List<String> qualityRules() {
        return qualityRules;
    }

    public void qualityRules(List<String> qualityRules) {
        this.qualityRules = qualityRules == null ? List.of() : List.copyOf(qualityRules);
    }

    public IngestTaskStatus status() {
        return status;
    }

    public void status(IngestTaskStatus status) {
        this.status = status;
    }
}
