package com.platform.pipeline.ingest;

import com.platform.pipeline.ingest.sync.OffsetStore;

import java.net.URI;
import java.util.List;

public class AbstractSourceConnector implements SourceConnector {
    private final ProtocolAdapter adapter;
    private final ConnectorSpec spec;
    private final OffsetStore offsetStore;

    public AbstractSourceConnector(ProtocolAdapter adapter, ConnectorSpec spec, OffsetStore offsetStore) {
        this.adapter = adapter;
        this.spec = spec;
        this.offsetStore = offsetStore;
    }

    @Override
    public ConnectorSpec spec() {
        return spec;
    }

    @Override
    public ConnectorCheckResult check(URI endpoint) {
        try {
            adapter.fetch(endpoint);
            return ConnectorCheckResult.success();
        } catch (Exception ex) {
            return ConnectorCheckResult.failed(ex.getMessage());
        }
    }

    @Override
    public List<String> discover(URI endpoint) {
        return List.of();
    }

    @Override
    public RawDataBatch read(URI endpoint, long offset, int batchSize) {
        if (offset > 0) {
            return new RawDataBatch(List.of(), offset);
        }
        try {
            String payload = adapter.fetch(endpoint);
            return new RawDataBatch(List.of(payload), offset + 1);
        } catch (Exception ex) {
            throw new IllegalStateException("connector read failed: " + protocol(), ex);
        }
    }

    @Override
    public long checkpoint(long taskId, long offset) {
        offsetStore.put(checkpointKey(taskId, protocol()), offset);
        return offsetStore.get(checkpointKey(taskId, protocol()));
    }

    @Override
    public void close() {
    }

    @Override
    public String protocol() {
        return adapter.protocol();
    }

    public static String checkpointKey(long taskId, String protocol) {
        return taskId + ":" + protocol;
    }
}
