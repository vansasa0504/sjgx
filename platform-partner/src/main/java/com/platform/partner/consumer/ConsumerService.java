package com.platform.partner.consumer;

import com.platform.common.exception.BusinessException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class ConsumerService {
    private final AtomicLong ids = new AtomicLong(1);
    private final Map<Long, Consumer> consumers = new ConcurrentHashMap<>();
    private final Map<Long, ConsumerQuota> quotas = new ConcurrentHashMap<>();
    private final List<String> events = new ArrayList<>();
    private final ConsumerStateMachine stateMachine = new ConsumerStateMachine();

    public Consumer register(String code, String name, String businessLine, String systemType, String complianceLevel) {
        Consumer consumer = new Consumer(ids.getAndIncrement(), code, name, businessLine, systemType, complianceLevel);
        consumers.put(consumer.id(), consumer);
        events.add(consumer.id() + ":REGISTER");
        return consumer;
    }

    public Consumer apply(long consumerId, ConsumerEvent event) {
        Consumer consumer = require(consumerId);
        ConsumerStatus next = stateMachine.transit(consumer.status(), event);
        consumer.status(next);
        events.add(consumer.id() + ":" + event + ":" + next);
        return consumer;
    }

    public ConsumerQuota configureQuota(long consumerId, long maxRequests, long warnThreshold) {
        require(consumerId);
        ConsumerQuota quota = new ConsumerQuota(consumerId, maxRequests, warnThreshold, 0);
        quotas.put(consumerId, quota);
        apply(consumerId, ConsumerEvent.CONFIGURE_QUOTA);
        return quota;
    }

    public ConsumerQuota consume(long consumerId) {
        ConsumerQuota quota = quotas.get(consumerId);
        if (quota == null) {
            throw new BusinessException("CONSUMER-404", "quota not configured");
        }
        long next = quota.usedRequests() + 1;
        if (next > quota.maxRequests()) {
            events.add(consumerId + ":QUOTA_EXCEEDED");
            throw new BusinessException("CONSUMER-429", "quota exceeded");
        }
        if (next >= quota.warnThreshold()) {
            events.add(consumerId + ":QUOTA_WARNING");
        }
        ConsumerQuota updated = quota.used(next);
        quotas.put(consumerId, updated);
        return updated;
    }

    public List<String> events() {
        return List.copyOf(events);
    }

    private Consumer require(long id) {
        Consumer consumer = consumers.get(id);
        if (consumer == null) {
            throw new BusinessException("CONSUMER-404", "consumer not found");
        }
        return consumer;
    }
}
