package com.platform.pipeline.ingest.adapter;

import com.platform.pipeline.ingest.ProtocolAdapter;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class KafkaAdapter implements ProtocolAdapter {
    private final Supplier<Consumer<String, String>> consumerSupplier;
    private final Duration pollTimeout;

    public KafkaAdapter(String bootstrapServers) {
        this(() -> new KafkaConsumer<>(props(bootstrapServers)), Duration.ofSeconds(3));
    }

    public KafkaAdapter(Supplier<Consumer<String, String>> consumerSupplier, Duration pollTimeout) {
        this.consumerSupplier = consumerSupplier;
        this.pollTimeout = pollTimeout;
    }

    @Override
    public String protocol() { return "KAFKA"; }

    @Override
    public String fetch(URI endpoint) {
        String topic = endpoint.getSchemeSpecificPart();
        try (Consumer<String, String> consumer = consumerSupplier.get()) {
            consumer.subscribe(List.of(topic));
            ConsumerRecords<String, String> records = consumer.poll(pollTimeout);
            return StreamSupport.stream(records.spliterator(), false)
                    .map(record -> record.value())
                    .collect(Collectors.joining("\n", "[", "]"));
        }
    }

    private static Properties props(String bootstrapServers) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "sjgx-ingest-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return props;
    }
}
