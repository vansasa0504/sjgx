package com.platform.pipeline.service;

import org.springframework.kafka.core.KafkaTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AsyncInvokeLogWriter {
    private final KafkaTemplate<String, ServiceInvokeLog> kafkaTemplate;
    private final String topic;
    private final List<ServiceInvokeLog> localMirror = new CopyOnWriteArrayList<>();

    public AsyncInvokeLogWriter() {
        this(null, "service-invoke-logs");
    }

    public AsyncInvokeLogWriter(KafkaTemplate<String, ServiceInvokeLog> kafkaTemplate, String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void write(ServiceInvokeLog log) {
        localMirror.add(log);
        if (kafkaTemplate != null) {
            kafkaTemplate.send(topic, log.serviceCode(), log);
        }
    }

    public List<ServiceInvokeLog> logs() {
        return new ArrayList<>(localMirror);
    }
}
