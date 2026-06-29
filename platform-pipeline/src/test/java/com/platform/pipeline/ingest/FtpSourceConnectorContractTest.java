package com.platform.pipeline.ingest;

class FtpSourceConnectorContractTest extends AbstractSourceConnectorContractTest {
    @Override
    String protocol() {
        return "FTP";
    }
}
