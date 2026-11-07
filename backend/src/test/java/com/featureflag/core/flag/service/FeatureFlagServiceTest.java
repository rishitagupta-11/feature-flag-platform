
package com.featureflag.core.flag.service;

import com.featureflag.core.flag.cache.CachedFlag;
import com.featureflag.core.flag.cache.FeatureFlagCache;
import com.featureflag.core.flag.dto.EvaluateFlagResponse;
import com.featureflag.core.flag.entity.FeatureFlag;
import com.featureflag.core.flag.event.FlagChangedEvent;
import com.featureflag.core.flag.repository.FeatureFlagRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.ApplicationEventPublisher;


import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;

@ExtendWith(MockitoExtension.class)
class FeatureFlagServiceTest {

    @Mock
    private FeatureFlagRepository repository;

    @Mock
    private FeatureFlagCache flagCache;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private FeatureFlagService service;

    private FeatureFlag createFlag(boolean enabled, int rolloutPercentage) {
        FeatureFlag flag = new FeatureFlag();
        flag.setFlagKey("new_checkout");
        flag.setEnabled(enabled);
        flag.setRolloutPercentage(rolloutPercentage);
        return flag;
    }

    private CachedFlag createCachedFlag(boolean enabled, int rolloutPercentage) {
        return new CachedFlag(
                UUID.randomUUID(),
                "new_checkout",
                enabled,
                rolloutPercentage
        );
    }

    @BeforeEach
    void setUp() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();

        service = new FeatureFlagService(
                repository,
                flagCache,
                eventPublisher,
                meterRegistry
        );
    }

    @Test
    void shouldUseRedisWhenCacheHit() {
        CachedFlag cachedFlag = createCachedFlag(true, 100);
        when(flagCache.get("new_checkout"))
                .thenReturn(Optional.of(cachedFlag));

        EvaluateFlagResponse response =
                service.evaluateFlag("new_checkout", "user_123");

        assertTrue(response.enabled());
        assertEquals("ROLLOUT_INCLUDED", response.reason());
        verify(repository, never()).findByFlagKey(anyString());
    }

    @Test
    void shouldFetchFromDatabaseAndPopulateCacheOnCacheMiss() {
        FeatureFlag dbFlag = createFlag(true, 100);

        when(flagCache.get("new_checkout"))
                .thenReturn(Optional.empty());
        when(repository.findByFlagKey("new_checkout"))
                .thenReturn(Optional.of(dbFlag));

        EvaluateFlagResponse response =
                service.evaluateFlag("new_checkout", "user_123");

        assertTrue(response.enabled());
        verify(repository).findByFlagKey("new_checkout");
        verify(flagCache).put(argThat(cached ->
                cached.flagKey().equals("new_checkout")
                        && cached.enabled()
                        && cached.rolloutPercentage() == 100
        ));
    }

    @Test
    void shouldReturnFalseWhenFlagDoesNotExist() {
        when(flagCache.get("new_checkout"))
                .thenReturn(Optional.empty());
        when(repository.findByFlagKey("new_checkout"))
                .thenReturn(Optional.empty());

        EvaluateFlagResponse response =
                service.evaluateFlag("new_checkout", "user_123");

        assertFalse(response.enabled());
        assertEquals("FLAG_NOT_FOUND", response.reason());
    }

    @Test
    void shouldReturnFalseWhenFlagIsDisabled() {
        when(flagCache.get("new_checkout"))
                .thenReturn(Optional.of(createCachedFlag(false, 100)));

        EvaluateFlagResponse response =
                service.evaluateFlag("new_checkout", "user_123");

        assertFalse(response.enabled());
        assertEquals("FLAG_DISABLED", response.reason());
    }

    @Test
    void shouldExcludeEveryUserWhenRolloutIsZero() {
        when(flagCache.get("new_checkout"))
                .thenReturn(Optional.of(createCachedFlag(true, 0)));

        for (int i = 1; i <= 100; i++) {
            EvaluateFlagResponse response =
                    service.evaluateFlag("new_checkout", "user_" + i);

            assertFalse(response.enabled());
            assertEquals("ROLLOUT_EXCLUDED", response.reason());
        }
    }

    @Test
    void shouldIncludeEveryUserWhenRolloutIs100() {
        when(flagCache.get("new_checkout"))
                .thenReturn(Optional.of(createCachedFlag(true, 100)));

        for (int i = 1; i <= 100; i++) {
            EvaluateFlagResponse response =
                    service.evaluateFlag("new_checkout", "user_" + i);

            assertTrue(response.enabled());
            assertEquals("ROLLOUT_INCLUDED", response.reason());
        }
    }

    @Test
    void shouldReturnConsistentResultForSameUser() {
        when(flagCache.get("new_checkout"))
                .thenReturn(Optional.of(createCachedFlag(true, 25)));

        EvaluateFlagResponse first =
                service.evaluateFlag("new_checkout", "user_123");
        EvaluateFlagResponse second =
                service.evaluateFlag("new_checkout", "user_123");
        EvaluateFlagResponse third =
                service.evaluateFlag("new_checkout", "user_123");

        assertEquals(first, second);
        assertEquals(second, third);
    }

    @Test
    void shouldDistributeUsersApproximatelyAccordingToRolloutPercentage() {
        when(flagCache.get("new_checkout"))
                .thenReturn(Optional.of(createCachedFlag(true, 25)));

        int totalUsers = 10_000;
        int enabledUsers = 0;

        for (int i = 1; i <= totalUsers; i++) {
            EvaluateFlagResponse response =
                    service.evaluateFlag("new_checkout", "user_" + i);

            if (response.enabled()) {
                enabledUsers++;
            }
        }

        double actualPercentage =
                (enabledUsers * 100.0) / totalUsers;

        assertTrue(
                actualPercentage >= 22.0 && actualPercentage <= 28.0,
                "Expected approximately 25% rollout, but got "
                        + actualPercentage + "%"
        );
    }

    @Test
    void shouldPublishEventWhenFlagIsUpdated() {
        UUID flagId = UUID.randomUUID();
        FeatureFlag existingFlag = createFlag(true, 25);

        when(repository.findById(flagId))
                .thenReturn(Optional.of(existingFlag));
        when(repository.save(existingFlag))
                .thenReturn(existingFlag);

        service.updateFlag(
                flagId,
                "New Checkout",
                "Updated description",
                true,
                100
        );

        verify(repository).save(existingFlag);
        verify(eventPublisher).publishEvent(
                new FlagChangedEvent("new_checkout")
        );
        verifyNoInteractions(flagCache);
    }

    @Test
    void shouldPublishEventWhenFlagIsDeleted() {
        UUID flagId = UUID.randomUUID();
        FeatureFlag existingFlag = createFlag(true, 25);

        when(repository.findById(flagId))
                .thenReturn(Optional.of(existingFlag));

        service.deleteFlag(flagId);

        verify(repository).delete(existingFlag);
        verify(eventPublisher).publishEvent(
                new FlagChangedEvent("new_checkout")
        );
        verifyNoInteractions(flagCache);
    }
}
