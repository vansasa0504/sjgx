package com.platform.pipeline.ingest.adapter;

public class MqAdapter extends KafkaAdapter {
    @Override
    public String protocol() { return "MQ"; }
}
