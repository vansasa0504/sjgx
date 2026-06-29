package com.platform.pipeline.ingest;

class ApiGatewaySourceConnectorContractTest extends AbstractSourceConnectorContractTest {
    @Override
    String protocol() {
        return "API_GW";
    }
}
