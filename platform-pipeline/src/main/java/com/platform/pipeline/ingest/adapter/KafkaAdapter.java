package com.platform.pipeline.ingest.adapter;

import com.platform.pipeline.ingest.ProtocolAdapter;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class KafkaAdapter implements ProtocolAdapter {
    private final Map<String, String> topicPayloads = new ConcurrentHashMap<>();

    public void put(String topic, String payload) { topicPayloads.put(topic, payload); }

    @Override
    public String protocol() { return "KAFKA"; }

    @Override
    public String fetch(URI endpoint) {
        return topicPayloads.getOrDefault(endpoint.getSchemeSpecificPart(), "[]");
    }
}
