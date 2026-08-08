package com.sashank.DigitalTwinBackend.cache;

import java.time.Duration;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Cache-aside helper for expensive query results (e.g. PostGIS distance queries), backed by
 * Redis with a per-call TTL. Every call logs whether it was a hit or a miss along with elapsed
 * time, so cached vs. uncached response times can be compared directly in the logs.
 */
@Component
public class QueryCache {

    private static final Logger log = LoggerFactory.getLogger(QueryCache.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public QueryCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public <T> T getOrCompute(String key, Duration ttl, TypeReference<T> type, Supplier<T> loader) {
        long start = System.nanoTime();
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            T value = readJson(cached, type);
            log.info("query cache HIT  key={} tookMs={}", key, elapsedMs(start));
            return value;
        }

        T value = loader.get();
        log.info("query cache MISS key={} tookMs={} (computed from PostGIS)", key, elapsedMs(start));
        redisTemplate.opsForValue().set(key, writeJson(value), ttl);
        return value;
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize cache value", e);
        }
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to deserialize cached value: " + json, e);
        }
    }
}