package com.platform.pipeline.service;

import com.platform.common.log.JdbcServiceInvokeLogRepository;
import com.platform.common.model.ServiceInvokeLog;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import com.platform.common.model.Page;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AsyncInvokeLogWriter {
    private final KafkaTemplate<String, ServiceInvokeLog> kafkaTemplate;
    private final String topic;
    private final JdbcServiceInvokeLogRepository repository;
    private final List<ServiceInvokeLog> localMirror = new CopyOnWriteArrayList<>();

    public AsyncInvokeLogWriter() {
        this(null, "service-invoke-logs", null);
    }

    public AsyncInvokeLogWriter(JdbcTemplate jdbcTemplate) {
        this(null, "service-invoke-logs", jdbcTemplate == null ? null : new JdbcServiceInvokeLogRepository(jdbcTemplate));
    }

    public AsyncInvokeLogWriter(KafkaTemplate<String, ServiceInvokeLog> kafkaTemplate, String topic) {
        this(kafkaTemplate, topic, null);
    }

    public AsyncInvokeLogWriter(KafkaTemplate<String, ServiceInvokeLog> kafkaTemplate, String topic,
                                JdbcServiceInvokeLogRepository repository) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.repository = repository;
    }

    public void write(ServiceInvokeLog log) {
        if (repository != null) {
            repository.save(log);
        } else {
            localMirror.add(log);
        }
        if (kafkaTemplate != null) {
            kafkaTemplate.send(topic, log.serviceCode(), log);
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

