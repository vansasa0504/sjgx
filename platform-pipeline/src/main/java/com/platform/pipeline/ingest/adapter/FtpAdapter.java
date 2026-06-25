package com.platform.pipeline.ingest.adapter;

import com.platform.pipeline.ingest.ProtocolAdapter;
import org.apache.commons.net.ftp.FTPClient;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public class FtpAdapter implements ProtocolAdapter {
    private final FtpClientFactory clientFactory;

    public FtpAdapter() {
        this(FTPClient::new);
    }

    FtpAdapter(FtpClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    public String protocol() { return "FTP"; }

    @Override
    public String fetch(URI endpoint) {
        FTPClient client = clientFactory.create();
        try {
            String[] userInfo = endpoint.getUserInfo().split(":", 2);
            client.connect(endpoint.getHost(), endpoint.getPort() > 0 ? endpoint.getPort() : 21);
            if (!client.login(userInfo[0], userInfo.length > 1 ? userInfo[1] : "")) {
                throw new IllegalStateException("FTP login failed");
            }
            client.enterLocalPassiveMode();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!client.retrieveFile(endpoint.getPath(), output)) {
                throw new IllegalStateException("FTP retrieve failed: " + client.getReplyString());
            }
            return output.toString(StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("FTP fetch failed", ex);
        } finally {
            try { if (client.isConnected()) client.disconnect(); } catch (Exception ignored) { }
        }
    }

    interface FtpClientFactory {
        FTPClient create();
    }
}
