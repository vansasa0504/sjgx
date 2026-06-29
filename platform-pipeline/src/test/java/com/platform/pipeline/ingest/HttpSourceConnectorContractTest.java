package com.platform.pipeline.ingest;

class HttpSourceConnectorContractTest extends AbstractSourceConnectorContractTest {
    @Override
    String protocol() {
        return "HTTP";
    }
}
