package com.platform.pipeline.service;

import com.platform.common.log.JdbcServiceInvokeLogRepository;
import com.platform.common.model.ServiceInvokeLog;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.platform.common.model.Page;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AsyncInvokeLogWriter {
    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncInvokeLogWriter.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final JdbcServiceInvokeLogRepository repository;
    private final ObjectMapper objectMapper;
    private final List<ServiceInvokeLog> localMirror = new CopyOnWriteArrayList<>();

    public AsyncInvokeLogWriter() {
        this(null, "service-invoke-logs", null, new ObjectMapper());
    }

    public AsyncInvokeLogWriter(JdbcTemplate jdbcTemplate) {
        this(null, "service-invoke-logs", jdbcTemplate == null ? null : new JdbcServiceInvokeLogRepository(jdbcTemplate), new ObjectMapper());
    }

    public AsyncInvokeLogWriter(KafkaTemplate<String, String> kafkaTemplate, String topic) {
        this(kafkaTemplate, topic, null, new ObjectMapper());
    }

    public AsyncInvokeLogWriter(KafkaTemplate<String, String> kafkaTemplate, String topic,
                                JdbcServiceInvokeLogRepository repository) {
        this(kafkaTemplate, topic, repository, new ObjectMapper());
    }

    public AsyncInvokeLogWriter(KafkaTemplate<String, String> kafkaTemplate, String topic,
                                JdbcServiceInvokeLogRepository repository, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void write(ServiceInvokeLog log) {
        if (kafkaTemplate != null) {
            try {
                kafkaTemplate.send(topic, log.serviceCode(), objectMapper.writeValueAsString(log));
                return;
            } catch (Exception ex) {
                if (repository == null) {
                    throw new IllegalStateException("invoke log kafka write failed", ex);
                }
                LOGGER.warn("Kafka invoke-log write failed, falling back to JDBC persistence", ex);
            }
        }
        if (repository != null) {
            repository.save(log);
        } else {
            localMirror.add(log);
        }
    }

    public void persistFromKafka(String payload) {
        try {
            ServiceInvokeLog log = objectMapper.readValue(payload, ServiceInvokeLog.class);
            if (repository != null) {
                repository.save(log);
            } else {
                localMirror.add(log);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("invoke log kafka consume failed", ex);
        }
    }

    public Page<ServiceInvokeLog> findByService(String serviceCode, String consumerCode, String status, int page, int size) {
        if (repository != null) {
            return repository.findByService(serviceCode, consumerCode, status, page, size);
        }
        List<ServiceInvokeLog> filtered = logs().stream()
                .filter(l -> serviceCode == null || serviceCode.isBlank() || serviceCode.equals(l.serviceCode()))
                .filter(l -> consumerCode == null || consumerCode.isBlank() || consumerCode.equals(l.consumerCode()))
                .filter(l -> status == null || status.isBlank() || status.equals(String.valueOf(l.status())))
                .toList();
        int safeSize = size <= 0 ? 10 : size;
        int safePage = page <= 0 ? 1 : page;
        int from = Math.min((safePage - 1) * safeSize, filtered.size());
        int to = Math.min(from + safeSize, filtered.size());
        return Page.of(new ArrayList<>(filtered.subList(from, to)), filtered.size(), safePage, safeSize);
    }

    public boolean hasRepository() {
        return repository != null;
    }

    public List<ServiceInvokeLog> logs() {
        if (repository != null) {
            return repository.findAll();
        }
        return new ArrayList<>(localMirror);
    }
}

