
package com.featureflag.core.flag.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import com.featureflag.core.flag.dto.EvaluateFlagResponse;
import com.featureflag.core.flag.entity.FeatureFlag;
import com.featureflag.core.flag.repository.FeatureFlagRepository;
import com.featureflag.core.flag.service.FeatureFlagService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeatureFlagCacheTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private FeatureFlagCache featureFlagCache;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        featureFlagCache = new FeatureFlagCache(
                redisTemplate,
                new ObjectMapper(),
                new SimpleMeterRegistry()
        );
    }

    @Test
    void shouldReturnCacheMissWhenRedisIsUnavailable() {

        when(valueOperations.get("feature-flag:new_checkout"))
                .thenThrow(new RedisConnectionFailureException(
                        "Redis is unavailable"
                ));

        Optional<CachedFlag> result =
                featureFlagCache.get("new_checkout");

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldFallbackToDatabaseWhenRedisIsUnavailable() {

        // Arrange: Redis is unavailable.
        when(valueOperations.get("feature-flag:new_checkout"))
                .thenThrow(new RedisConnectionFailureException(
                        "Redis is unavailable"
                ));

        FeatureFlagRepository repository =
                mock(FeatureFlagRepository.class);

        FeatureFlag dbFlag = new FeatureFlag();
        dbFlag.setFlagKey("new_checkout");
        dbFlag.setEnabled(true);
        dbFlag.setRolloutPercentage(100);

        when(repository.findByFlagKey("new_checkout"))
                .thenReturn(Optional.of(dbFlag));

        FeatureFlagService service = new FeatureFlagService(
                repository,
                featureFlagCache,
                mock(org.springframework.context.ApplicationEventPublisher.class),
                new SimpleMeterRegistry()
        );

        // Act: Evaluate the flag.
        EvaluateFlagResponse response =
                service.evaluateFlag("new_checkout", "user_123");

        // Assert: Evaluation succeeds using PostgreSQL.
        assertTrue(response.enabled());
        assertEquals("ROLLOUT_INCLUDED", response.reason());

        verify(repository).findByFlagKey("new_checkout");
    }


    @Test
    void shouldNotThrowExceptionWhenRedisWriteFails() {

        CachedFlag flag = new CachedFlag(
                java.util.UUID.randomUUID(),
                "new_checkout",
                true,
                100
        );

        doThrow(new RedisConnectionFailureException("Redis unavailable"))
                .when(valueOperations)
                .set(
                        eq("feature-flag:new_checkout"),
                        anyString(),
                        any(java.time.Duration.class)
                );

        assertDoesNotThrow(() -> featureFlagCache.put(flag));
    }

    @Test
    void shouldNotThrowExceptionWhenRedisEvictionFails() {

        doThrow(new RedisConnectionFailureException("Redis unavailable"))
                .when(redisTemplate)
                .delete("feature-flag:new_checkout");

        assertDoesNotThrow(() ->
                featureFlagCache.evict("new_checkout")
        );
    }

}
