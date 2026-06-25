package com.platform.pipeline.ingest;

import java.io.IOException;
import java.net.URI;

public interface ProtocolAdapter {
    String protocol();

    String fetch(URI endpoint) throws IOException, InterruptedException;
}
