package com.platform.partner.consumer;

import com.platform.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RedisQuotaCounter implements QuotaCounter {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisQuotaCounter.class);
    private static final String LUA = "local current = redis.call('INCR', KEYS[1]); if current > tonumber(ARGV[1]) then return -1 else return current end";
    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<Long> script;
    private final QuotaCounter fallback;

    public RedisQuotaCounter(RedisTemplate<String, String> redisTemplate) {
        this(redisTemplate, new LocalFallbackQuotaCounter());
    }

    public RedisQuotaCounter(RedisTemplate<String, String> redisTemplate, QuotaCounter fallback) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>(LUA, Long.class);
        this.fallback = fallback == null ? new LocalFallbackQuotaCounter() : fallback;
    }

    @Override
    public long incrementAndCheck(long consumerId, long maxRequests) {
        Long result;
        try {
            result = redisTemplate.execute(script, List.of("consumer:quota:" + consumerId), String.valueOf(maxRequests));
        } catch (RuntimeException ex) {
            LOGGER.warn("Redis quota counter unavailable, falling back to local quota counter for consumer {}", consumerId, ex);
            return fallback.incrementAndCheck(consumerId, maxRequests);
        }
        if (result == null) {
            LOGGER.warn("Redis quota counter returned null, falling back to local quota counter for consumer {}", consumerId);
            return fallback.incrementAndCheck(consumerId, maxRequests);
        }
        if (result < 0) {
            throw new BusinessException("CONSUMER-429", "quota exceeded");
        }
        return result;
    }

    /**
     * Degraded mode is local to this JVM. It keeps the service available during
     * Redis outages, but distributed quota precision is restored only after Redis
     * recovers and the normal Redis counter is used again.
     */
    private static final class LocalFallbackQuotaCounter implements QuotaCounter {
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
