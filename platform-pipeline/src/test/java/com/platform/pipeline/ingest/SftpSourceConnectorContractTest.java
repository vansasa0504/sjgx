package com.platform.pipeline.ingest;

class SftpSourceConnectorContractTest extends AbstractSourceConnectorContractTest {
    @Override
    String protocol() {
        return "SFTP";
    }
}
