package com.platform.pipeline.ingest.sync;

import org.springframework.data.redis.core.RedisTemplate;

public class RedisOffsetStore implements OffsetStore {
    private final RedisTemplate<String, String> redisTemplate;

    public RedisOffsetStore(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public long get(String key) {
        String value = redisTemplate.opsForValue().get(redisKey(key));
        return value == null ? 0L : Long.parseLong(value);
    }

    @Override
    public void put(String key, long offset) {
        redisTemplate.opsForValue().set(redisKey(key), String.valueOf(Math.max(get(key), offset)));
    }

    private String redisKey(String key) {
        return "ingest:offset:" + key;
    }
}
