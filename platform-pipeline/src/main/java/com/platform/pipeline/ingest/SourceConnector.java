package com.platform.pipeline.ingest;

import java.net.URI;
import java.util.List;

public interface SourceConnector extends AutoCloseable {
    ConnectorSpec spec();

    ConnectorCheckResult check(URI endpoint);

    List<String> discover(URI endpoint);

    RawDataBatch read(URI endpoint, long offset, int batchSize);

    long checkpoint(long taskId, long offset);

    @Override
    void close();

    String protocol();
}
