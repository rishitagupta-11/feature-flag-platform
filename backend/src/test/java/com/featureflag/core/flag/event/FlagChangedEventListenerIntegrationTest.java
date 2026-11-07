
package com.featureflag.core.flag.event;

import com.featureflag.core.flag.cache.CachedFlag;
import com.featureflag.core.flag.cache.FeatureFlagCache;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FlagChangedEventListenerIntegrationTest {

    @Autowired
    private FeatureFlagCache flagCache;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void shouldEvictCacheAfterSuccessfulCommit() {

        String flagKey = "commit_test_" + UUID.randomUUID();

        CachedFlag cachedFlag = new CachedFlag(
                UUID.randomUUID(),
                flagKey,
                true,
                100
        );

        flagCache.put(cachedFlag);

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {

            eventPublisher.publishEvent(
                    new FlagChangedEvent(flagKey)
            );

            // Cache should still exist before commit.
            assertTrue(flagCache.get(flagKey).isPresent());
        });

        // Cache should be evicted after successful commit.
        assertTrue(flagCache.get(flagKey).isEmpty());
    }

    @Test
    void shouldNotEvictCacheAfterRollback() {

        String flagKey = "rollback_test_" + UUID.randomUUID();

        CachedFlag cachedFlag = new CachedFlag(
                UUID.randomUUID(),
                flagKey,
                true,
                100
        );

        flagCache.put(cachedFlag);

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {

            eventPublisher.publishEvent(
                    new FlagChangedEvent(flagKey)
            );

            // Explicitly roll back the transaction.
            status.setRollbackOnly();
        });

        // Cache should remain because the transaction rolled back.
        assertTrue(flagCache.get(flagKey).isPresent());

        // Clean up the test entry.
        flagCache.evict(flagKey);
    }
}
