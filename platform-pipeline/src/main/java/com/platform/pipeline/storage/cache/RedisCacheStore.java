package com.platform.pipeline.storage.cache;

import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.Optional;

public class RedisCacheStore {
    private final RedisTemplate<String, String> redisTemplate;
    private final Duration ttl;

    public RedisCacheStore(RedisTemplate<String, String> redisTemplate, Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    public void put(String key, String value) {
        redisTemplate.opsForValue().set(redisKey(key), value, ttl);
    }

    public Optional<String> get(String key) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(redisKey(key)));
    }

    public void invalidate(String key) {
        redisTemplate.delete(redisKey(key));
    }

    private String redisKey(String key) {
        return "storage:cache:" + key;
    }
}
