package com.platform.pipeline.ingest;

import java.util.List;

public record ConnectorSpec(
        String protocol,
        List<String> formats,
        List<String> syncModes,
        boolean supportsCheckpoint,
        boolean supportsDiscover) {
}
