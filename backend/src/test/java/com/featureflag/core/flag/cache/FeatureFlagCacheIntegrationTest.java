
package com.featureflag.core.flag.cache;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FeatureFlagCacheIntegrationTest {

    @Autowired
    private FeatureFlagCache flagCache;

    @Test
    void shouldStoreAndRetrieveFeatureFlagFromRedis() {

        String flagKey = "redis-integration-" + UUID.randomUUID();

        CachedFlag flag = new CachedFlag(
                UUID.randomUUID(),
                flagKey,
                true,
                35
        );

        // Store the flag in Redis
        flagCache.put(flag);

        // Retrieve the flag from Redis
        Optional<CachedFlag> result = flagCache.get(flagKey);

        assertTrue(result.isPresent());
        assertEquals(flag, result.get());

        // Clean up
        flagCache.evict(flagKey);

        assertTrue(flagCache.get(flagKey).isEmpty());
    }
}
