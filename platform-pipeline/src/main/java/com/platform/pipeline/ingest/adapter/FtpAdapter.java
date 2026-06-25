package com.platform.pipeline.ingest.adapter;

public class FtpAdapter extends SftpAdapter {
    @Override
    public String protocol() { return "FTP"; }
}
