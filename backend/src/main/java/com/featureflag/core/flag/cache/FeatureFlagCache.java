
package com.featureflag.core.flag.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class FeatureFlagCache {

    private static final String CACHE_PREFIX = "feature-flag:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private final Counter cacheHits;
    private final Counter cacheMisses;
    private final Counter cacheReadErrors;
    private final Counter cacheWriteErrors;
    private final Counter cacheEvictionErrors;

    public FeatureFlagCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;

        this.cacheHits = Counter.builder("feature_flag_cache_hits")
                .description("Number of feature flag cache hits")
                .register(meterRegistry);

        this.cacheMisses = Counter.builder("feature_flag_cache_misses")
                .description("Number of feature flag cache misses")
                .register(meterRegistry);

        this.cacheReadErrors = Counter.builder("feature_flag_cache_read_errors")
                .description("Number of feature flag cache read errors")
                .register(meterRegistry);

        this.cacheWriteErrors = Counter.builder("feature_flag_cache_write_errors")
                .description("Number of feature flag cache write errors")
                .register(meterRegistry);

        this.cacheEvictionErrors = Counter.builder("feature_flag_cache_eviction_errors")
                .description("Number of feature flag cache eviction errors")
                .register(meterRegistry);
    }

    public Optional<CachedFlag> get(String flagKey) {
        try {
            String json = redisTemplate.opsForValue()
                    .get(CACHE_PREFIX + flagKey);

            if (json == null) {
                cacheMisses.increment();
                return Optional.empty();
            }

            CachedFlag flag = objectMapper.readValue(json, CachedFlag.class);

            cacheHits.increment();
            return Optional.of(flag);

        } catch (Exception exception) {
            cacheReadErrors.increment();
            return Optional.empty();
        }
    }

    public void put(CachedFlag flag) {
        try {
            String json = objectMapper.writeValueAsString(flag);

            redisTemplate.opsForValue().set(
                    CACHE_PREFIX + flag.flagKey(),
                    json,
                    CACHE_TTL
            );

        } catch (Exception exception) {
            cacheWriteErrors.increment();
        }
    }

    public void evict(String flagKey) {
        try {
            redisTemplate.delete(CACHE_PREFIX + flagKey);

        } catch (Exception exception) {
            cacheEvictionErrors.increment();
        }
    }
}
