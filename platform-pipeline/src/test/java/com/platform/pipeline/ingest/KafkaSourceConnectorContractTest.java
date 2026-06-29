package com.platform.pipeline.ingest;

class KafkaSourceConnectorContractTest extends AbstractSourceConnectorContractTest {
    @Override
    String protocol() {
        return "KAFKA";
    }
}
