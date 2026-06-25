package com.platform.pipeline.ingest.adapter;

import com.platform.pipeline.ingest.ProtocolAdapter;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

public class SftpAdapter implements ProtocolAdapter {
    @Override
    public String protocol() { return "SFTP"; }

    @Override
    public String fetch(URI endpoint) throws IOException {
        return Files.readString(Path.of(endpoint));
    }
}
