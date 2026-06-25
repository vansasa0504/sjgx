package com.platform.pipeline.ingest;

import java.net.URI;

public class IngestTask {
    private final long id;
    private final long partnerId;
    private final URI endpoint;
    private final String protocol;
    private final String format;
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

    public IngestTaskStatus status() {
        return status;
    }

    public void status(IngestTaskStatus status) {
        this.status = status;
    }
}
