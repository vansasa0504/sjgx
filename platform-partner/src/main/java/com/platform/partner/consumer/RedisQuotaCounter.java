package com.platform.partner.consumer;

import com.platform.common.exception.BusinessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

public class RedisQuotaCounter implements QuotaCounter {
    private static final String LUA = "local current = redis.call('INCR', KEYS[1]); if current > tonumber(ARGV[1]) then return -1 else return current end";
    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<Long> script;

    public RedisQuotaCounter(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>(LUA, Long.class);
    }

    @Override
    public long incrementAndCheck(long consumerId, long maxRequests) {
        Long result = redisTemplate.execute(script, List.of("consumer:quota:" + consumerId), String.valueOf(maxRequests));
        if (result == null) {
            throw new BusinessException("CONSUMER-500", "quota counter unavailable");
        }
        if (result < 0) {
            throw new BusinessException("CONSUMER-429", "quota exceeded");
        }
        return result;
    }
}
