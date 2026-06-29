package com.platform.pipeline.ingest;

class MqSourceConnectorContractTest extends AbstractSourceConnectorContractTest {
    @Override
    String protocol() {
        return "MQ";
    }
}
