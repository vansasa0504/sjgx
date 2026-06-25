package com.platform.pipeline.ingest.adapter;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.platform.pipeline.ingest.ProtocolAdapter;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class SftpAdapter implements ProtocolAdapter {
    private final JSch jsch;
    private final int timeoutMillis;

    public SftpAdapter() {
        this(new JSch(), 10_000);
    }

    SftpAdapter(JSch jsch, int timeoutMillis) {
        this.jsch = jsch;
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public String protocol() { return "SFTP"; }

    @Override
    public String fetch(URI endpoint) {
        Session session = null;
        ChannelSftp channel = null;
        try {
            String[] userInfo = endpoint.getUserInfo().split(":", 2);
            session = jsch.getSession(userInfo[0], endpoint.getHost(), endpoint.getPort() > 0 ? endpoint.getPort() : 22);
            session.setPassword(userInfo.length > 1 ? userInfo[1] : "");
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect(timeoutMillis);
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(timeoutMillis);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            channel.get(endpoint.getPath(), output);
            return output.toString(StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("SFTP fetch failed", ex);
        } finally {
            if (channel != null) channel.disconnect();
            if (session != null) session.disconnect();
        }
    }
}
