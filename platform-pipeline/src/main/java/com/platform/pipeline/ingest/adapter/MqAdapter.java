package com.platform.pipeline.ingest.adapter;

import com.platform.pipeline.ingest.ProtocolAdapter;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.net.URI;

public class MqAdapter implements ProtocolAdapter {
    private final RabbitTemplate rabbitTemplate;

    public MqAdapter(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public String protocol() { return "MQ"; }

    @Override
    public String fetch(URI endpoint) {
        Object payload = rabbitTemplate.receiveAndConvert(endpoint.getSchemeSpecificPart());
        return payload == null ? "[]" : String.valueOf(payload);
    }
}
