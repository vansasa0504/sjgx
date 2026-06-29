package com.platform.pipeline.ingest;

class DbSourceConnectorContractTest extends AbstractSourceConnectorContractTest {
    @Override
    String protocol() {
        return "DB";
    }
}
