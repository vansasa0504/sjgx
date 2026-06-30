package com.platform.partner.consumer;

import com.platform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumerServiceTest {
    @Test
    void managesLifecycleAndBlocksQuotaExhaustion() {
        ConsumerService service = new ConsumerService();
        Consumer consumer = service.register("risk-core", "风控核心", "风险", "CORE", "L2");

        service.apply(consumer.id(), ConsumerEvent.SUBMIT);
        service.apply(consumer.id(), ConsumerEvent.APPROVE);
        service.configureQuota(consumer.id(), 2, 2);
        service.apply(consumer.id(), ConsumerEvent.ENABLE);
        service.consume(consumer.id());
        service.consume(consumer.id());

        assertEquals(ConsumerStatus.ENABLED, consumer.status());
        assertThrows(BusinessException.class, () -> service.consume(consumer.id()));
        assertTrue(service.events().stream().anyMatch(e -> e.contains("QUOTA_WARNING")));
        assertTrue(service.events().stream().anyMatch(e -> e.contains("QUOTA_EXCEEDED")));
    }

    @Test
    void redisQuotaCounterUsesLuaResultForQuotaDecision() {
        AtomicLong calls = new AtomicLong();
        RedisQuotaCounter counter = new RedisQuotaCounter(new RedisTemplate<>() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
                long next = calls.incrementAndGet();
                long max = Long.parseLong(String.valueOf(args[0]));
                return (T) Long.valueOf(next > max ? -1L : next);
            }
        });

        assertEquals(1, counter.incrementAndCheck(7L, 2));
        assertEquals(2, counter.incrementAndCheck(7L, 2));
        assertThrows(BusinessException.class, () -> counter.incrementAndCheck(7L, 2));
    }

    @Test
    void redisQuotaCounterFallsBackWhenRedisReturnsNull() {
        RedisQuotaCounter counter = new RedisQuotaCounter(new RedisTemplate<>() {
            @Override
            public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
                return null;
            }
        });

        assertEquals(1, counter.incrementAndCheck(9L, 2));
        assertEquals(2, counter.incrementAndCheck(9L, 2));
        assertThrows(BusinessException.class, () -> counter.incrementAndCheck(9L, 2));
    }

    @Test
    void redisQuotaCounterFallsBackWhenRedisThrows() {
        AtomicLong fallbackCalls = new AtomicLong();
        RedisQuotaCounter counter = new RedisQuotaCounter(new RedisTemplate<>() {
            @Override
            public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
                throw new IllegalStateException("redis down");
            }
        }, (consumerId, maxRequests) -> fallbackCalls.incrementAndGet());

        assertEquals(1, counter.incrementAndCheck(10L, 2));
        assertEquals(1, fallbackCalls.get());
    }

    @Test
    void rejectsIllegalLifecycleMove() {
        ConsumerService service = new ConsumerService();
        Consumer consumer = service.register("crm", "CRM", "营销", "APP", "L1");

        assertThrows(BusinessException.class, () -> service.apply(consumer.id(), ConsumerEvent.ENABLE));
    }
}
