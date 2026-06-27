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
    private final QuotaCounter quotaCounter;

    public ConsumerService() {
        this(new LocalQuotaCounter());
    }

    public ConsumerService(QuotaCounter quotaCounter) {
        this.quotaCounter = quotaCounter;
    }

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
        long next;
        try {
            next = quotaCounter.incrementAndCheck(consumerId, quota.maxRequests());
        } catch (BusinessException ex) {
            if ("CONSUMER-429".equals(ex.code())) {
                events.add(consumerId + ":QUOTA_EXCEEDED");
            }
            throw ex;
        }
        if (next >= quota.warnThreshold()) {
            events.add(consumerId + ":QUOTA_WARNING");
        }
        ConsumerQuota updated = quota.used(next);
        quotas.put(consumerId, updated);
        return updated;
    }

    public List<Consumer> list(String keyword, String businessLine, String status) {
        return consumers.values().stream()
                .filter(c -> keyword == null || keyword.isBlank()
                        || (c.name() != null && c.name().contains(keyword))
                        || (c.consumerCode() != null && c.consumerCode().contains(keyword)))
                .filter(c -> businessLine == null || businessLine.isBlank() || businessLine.equals(c.businessLine()))
                .filter(c -> status == null || status.isBlank() || status.equals(c.status().name()))
                .toList();
    }

    public Consumer find(long id) {
        Consumer consumer = consumers.get(id);
        if (consumer == null) {
            throw new BusinessException("CONSUMER-404", "consumer not found");
        }
        return consumer;
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

    private static class LocalQuotaCounter implements QuotaCounter {
        private final Map<Long, Long> counts = new ConcurrentHashMap<>();

        @Override
        public long incrementAndCheck(long consumerId, long maxRequests) {
            long next = counts.merge(consumerId, 1L, Long::sum);
            if (next > maxRequests) {
                throw new BusinessException("CONSUMER-429", "quota exceeded");
            }
            return next;
        }
    }
}
