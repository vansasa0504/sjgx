package com.platform.pipeline.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "pipeline.invoke-log", name = "kafka-enabled", havingValue = "true")
public class KafkaInvokeLogConsumer {
    private final AsyncInvokeLogWriter logWriter;

    public KafkaInvokeLogConsumer(AsyncInvokeLogWriter logWriter) {
        this.logWriter = logWriter;
    }

    @KafkaListener(
            topics = "${pipeline.invoke-log.topic:service-invoke-logs}",
            groupId = "${pipeline.invoke-log.group-id:platform-pipeline}"
    )
    public void consume(String payload) {
        logWriter.persistFromKafka(payload);
    }
}
