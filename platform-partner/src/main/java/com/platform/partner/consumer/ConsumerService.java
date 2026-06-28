package com.platform.partner.consumer;

import com.platform.common.exception.BusinessException;
import com.platform.common.log.JdbcServiceInvokeLogRepository;
import com.platform.common.model.Page;
import com.platform.common.model.ServiceInvokeLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import com.platform.common.db.IdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;

public class ConsumerService {
    private final AtomicLong ids = new AtomicLong(1);
    private final Map<Long, Consumer> consumers = new ConcurrentHashMap<>();
    private final Map<Long, ConsumerQuota> quotas = new ConcurrentHashMap<>();
    private final List<String> events = new ArrayList<>();
    private final ConsumerStateMachine stateMachine = new ConsumerStateMachine();
    private final QuotaCounter quotaCounter;
    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;
    private final JdbcServiceInvokeLogRepository invokeLogRepository;

    public ConsumerService() {
        this(new LocalQuotaCounter());
    }

    public ConsumerService(JdbcTemplate jdbcTemplate) {
        this(new LocalQuotaCounter(), jdbcTemplate);
    }

    public ConsumerService(QuotaCounter quotaCounter) {
        this(quotaCounter, null);
    }

    public ConsumerService(QuotaCounter quotaCounter, JdbcTemplate jdbcTemplate) {
        this.quotaCounter = quotaCounter;
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = jdbcTemplate != null ? new IdGenerator(jdbcTemplate) : null;
        this.invokeLogRepository = jdbcTemplate != null ? new JdbcServiceInvokeLogRepository(jdbcTemplate) : null;
    }

    private boolean useDb() {
        return jdbcTemplate != null;
    }

    public Consumer register(String code, String name, String businessLine, String systemType, String complianceLevel) {
        long id = useDb() ? idGenerator.nextId("t_consumer") : ids.getAndIncrement();
        Consumer consumer = new Consumer(id, code, name, businessLine, systemType, complianceLevel);
        if (useDb()) {
            jdbcTemplate.update("""
                    INSERT INTO t_consumer
                    (id, consumer_code, name, business_line, system_type, compliance_level, status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, consumer.id(), code, name, businessLine, systemType, complianceLevel, consumer.status().name());
            appendEvent(consumer.id(), "REGISTER", consumer.status().name(), consumer.status().name());
        }
        consumers.put(consumer.id(), consumer);
        events.add(consumer.id() + ":REGISTER");
        return consumer;
    }

    public Consumer apply(long consumerId, ConsumerEvent event) {
        Consumer consumer = require(consumerId);
        ConsumerStatus from = consumer.status();
        ConsumerStatus next = stateMachine.transit(consumer.status(), event);
        consumer.status(next);
        events.add(consumer.id() + ":" + event + ":" + next);
        if (useDb()) {
            jdbcTemplate.update("UPDATE t_consumer SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    next.name(), consumerId);
            appendEvent(consumerId, event.name(), from.name(), next.name());
        }
        return consumer;
    }

    public ConsumerQuota configureQuota(long consumerId, long maxRequests, long warnThreshold) {
        require(consumerId);
        ConsumerQuota quota = new ConsumerQuota(consumerId, maxRequests, warnThreshold, 0);
        quotas.put(consumerId, quota);
        if (useDb()) {
            jdbcTemplate.update("DELETE FROM t_consumer_quota WHERE consumer_id = ?", consumerId);
            jdbcTemplate.update("""
                    INSERT INTO t_consumer_quota
                    (id, consumer_id, max_requests, warn_threshold, used_requests, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, idGenerator.nextId("t_consumer_quota"), consumerId, maxRequests, warnThreshold, 0L);
        }
        apply(consumerId, ConsumerEvent.CONFIGURE_QUOTA);
        return quota;
    }

    public ConsumerQuota consume(long consumerId) {
        ConsumerQuota quota = useDb() ? loadQuota(consumerId) : quotas.get(consumerId);
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
        if (useDb()) {
            jdbcTemplate.update("UPDATE t_consumer_quota SET used_requests = ?, updated_at = CURRENT_TIMESTAMP WHERE consumer_id = ?",
                    next, consumerId);
        }
        return updated;
    }

    public List<Consumer> list(String keyword, String businessLine, String status) {
        List<Consumer> source = useDb() ? listConsumersFromDb() : List.copyOf(consumers.values());
        return source.stream()
                .filter(c -> keyword == null || keyword.isBlank()
                        || (c.name() != null && c.name().contains(keyword))
                        || (c.consumerCode() != null && c.consumerCode().contains(keyword)))
                .filter(c -> businessLine == null || businessLine.isBlank() || businessLine.equals(c.businessLine()))
                .filter(c -> status == null || status.isBlank() || status.equals(c.status().name()))
                .toList();
    }

    public Consumer find(long id) {
        Consumer consumer = useDb() ? loadConsumer(id) : consumers.get(id);
        if (consumer == null) {
            throw new BusinessException("CONSUMER-404", "consumer not found");
        }
        return consumer;
    }

    public List<String> events() {
        if (useDb()) {
            return jdbcTemplate.query("""
                    SELECT consumer_id, event, to_status FROM t_consumer_event ORDER BY id
                    """, (rs, rowNum) -> rs.getLong("consumer_id") + ":" + rs.getString("event")
                    + ":" + rs.getString("to_status"));
        }
        return List.copyOf(events);
    }

    public Page<ServiceInvokeLog> logs(long consumerId, int page, int size) {
        Consumer consumer = find(consumerId);
        if (!useDb()) {
            return Page.of(List.of(), 0, page <= 0 ? 1 : page, size <= 0 ? 10 : size);
        }
        return invokeLogRepository.findByConsumer(consumer.consumerCode(), page, size);
    }

    private Consumer require(long id) {
        Consumer consumer = useDb() ? loadConsumer(id) : consumers.get(id);
        if (consumer == null) {
            throw new BusinessException("CONSUMER-404", "consumer not found");
        }
        return consumer;
    }

    private void appendEvent(long consumerId, String event, String fromStatus, String toStatus) {
        jdbcTemplate.update("""
                INSERT INTO t_consumer_event
                (id, consumer_id, event, from_status, to_status, operator, created_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, idGenerator.nextId("t_consumer_event"), consumerId, event, fromStatus, toStatus, "system");
    }

    private List<Consumer> listConsumersFromDb() {
        return jdbcTemplate.query("SELECT * FROM t_consumer ORDER BY id", (rs, rowNum) -> mapConsumer(
                rs.getLong("id"), rs.getString("consumer_code"), rs.getString("name"),
                rs.getString("business_line"), rs.getString("system_type"), rs.getString("compliance_level"),
                rs.getString("status")));
    }

    private Consumer loadConsumer(long id) {
        return jdbcTemplate.query("SELECT * FROM t_consumer WHERE id = ?", (rs, rowNum) -> mapConsumer(
                rs.getLong("id"), rs.getString("consumer_code"), rs.getString("name"),
                rs.getString("business_line"), rs.getString("system_type"), rs.getString("compliance_level"),
                rs.getString("status")), id).stream().findFirst().orElse(null);
    }

    private Consumer mapConsumer(long id, String code, String name, String businessLine, String systemType,
                                 String complianceLevel, String status) {
        Consumer consumer = new Consumer(id, code, name, businessLine, systemType, complianceLevel);
        consumer.status(ConsumerStatus.valueOf(status));
        return consumer;
    }

    private ConsumerQuota loadQuota(long consumerId) {
        return jdbcTemplate.query("""
                SELECT consumer_id, max_requests, warn_threshold, used_requests
                FROM t_consumer_quota WHERE consumer_id = ?
                """, (rs, rowNum) -> new ConsumerQuota(rs.getLong("consumer_id"), rs.getLong("max_requests"),
                rs.getLong("warn_threshold"), rs.getLong("used_requests")), consumerId)
                .stream().findFirst().orElse(null);
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
