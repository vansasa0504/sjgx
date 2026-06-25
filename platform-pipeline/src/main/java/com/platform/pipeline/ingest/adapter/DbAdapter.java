package com.platform.pipeline.ingest.adapter;

import com.platform.pipeline.ingest.ProtocolAdapter;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DbAdapter implements ProtocolAdapter {
    private final Map<String, String> queryResults = new ConcurrentHashMap<>();

    public void put(String queryKey, String payload) { queryResults.put(queryKey, payload); }

    @Override
    public String protocol() { return "DB"; }

    @Override
    public String fetch(URI endpoint) {
        return queryResults.getOrDefault(endpoint.getSchemeSpecificPart(), "[]");
    }
}
