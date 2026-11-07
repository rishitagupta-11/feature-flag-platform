
package com.featureflag.core.flag.service;

import com.featureflag.core.flag.entity.FeatureFlag;
import com.featureflag.core.flag.repository.FeatureFlagRepository;
import com.featureflag.core.flag.dto.EvaluateFlagResponse;
import com.featureflag.core.flag.cache.CachedFlag;
import com.featureflag.core.flag.cache.FeatureFlagCache;
import com.featureflag.core.flag.event.FlagChangedEvent;
import com.featureflag.core.exception.ResourceNotFoundException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class FeatureFlagService {

    private final FeatureFlagRepository repository;

    private final FeatureFlagCache flagCache;

    private final ApplicationEventPublisher eventPublisher;

    private final Timer evaluationTimer;

    public FeatureFlagService(
            FeatureFlagRepository repository,
            FeatureFlagCache flagCache,
            ApplicationEventPublisher eventPublisher,
            MeterRegistry meterRegistry
    ) {
        this.repository = repository;
        this.flagCache = flagCache;
        this.eventPublisher = eventPublisher;

        this.evaluationTimer = Timer.builder("feature_flag_evaluation")
                .description("Time taken to evaluate a feature flag")
                .register(meterRegistry);
    }

    @Transactional
    public FeatureFlag createFlag(
            String flagKey,
            String name,
            String description,
            int rolloutPercentage) {

        // Check for duplicate flag keys
        if (repository.existsByFlagKey(flagKey)) {
            throw new IllegalArgumentException(
                    "A feature flag with this key already exists"
            );
        }

        // Validate rollout percentage
        if (rolloutPercentage < 0 || rolloutPercentage > 100) {
            throw new IllegalArgumentException(
                    "Rollout percentage must be between 0 and 100"
            );
        }

        // Create a new feature flag
        FeatureFlag flag = new FeatureFlag();
        flag.setFlagKey(flagKey);
        flag.setName(name);
        flag.setDescription(description);
        flag.setRolloutPercentage(rolloutPercentage);
        flag.setEnabled(false);

        // Save it to PostgreSQL
        return repository.save(flag);
    }
    @Transactional(readOnly = true)
    public List<FeatureFlag> getAllFlags() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public FeatureFlag getFlagById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Feature flag not found with id: " + id
                ));
    }

    @Transactional
    public FeatureFlag updateFlag(
            UUID id,
            String name,
            String description,
            boolean enabled,
            int rolloutPercentage) {

        FeatureFlag flag = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Feature flag not found with id: " + id
                ));

        if (rolloutPercentage < 0 || rolloutPercentage > 100) {
            throw new IllegalArgumentException(
                    "Rollout percentage must be between 0 and 100"
            );
        }

        flag.setName(name);
        flag.setDescription(description);
        flag.setEnabled(enabled);
        flag.setRolloutPercentage(rolloutPercentage);

        FeatureFlag updatedFlag = repository.save(flag);

        eventPublisher.publishEvent(
                new FlagChangedEvent(updatedFlag.getFlagKey())
        );

        return updatedFlag;
    }

    @Transactional
    public void deleteFlag(UUID id) {
        FeatureFlag flag = repository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Feature flag not found with id: " + id
                ));

        repository.delete(flag);

        eventPublisher.publishEvent(
                new FlagChangedEvent(flag.getFlagKey())
        );
    }


    @Transactional(readOnly = true)
    public EvaluateFlagResponse evaluateFlag(String flagKey, String userId) {
        return evaluationTimer.record(() -> {

            CachedFlag flag = flagCache.get(flagKey)
                    .orElseGet(() -> repository.findByFlagKey(flagKey)
                            .map(dbFlag -> {
                                CachedFlag cachedFlag = new CachedFlag(
                                        dbFlag.getId(),
                                        dbFlag.getFlagKey(),
                                        dbFlag.isEnabled(),
                                        dbFlag.getRolloutPercentage()
                                );

                                flagCache.put(cachedFlag);
                                return cachedFlag;
                            })
                            .orElse(null));

            if (flag == null) {
                return new EvaluateFlagResponse(
                        flagKey, userId, false, "FLAG_NOT_FOUND"
                );
            }

            if (!flag.enabled()) {
                return new EvaluateFlagResponse(
                        flagKey, userId, false, "FLAG_DISABLED"
                );
            }

            int bucket = calculateBucket(flagKey, userId);
            boolean enabled = bucket < flag.rolloutPercentage();

            String reason = enabled
                    ? "ROLLOUT_INCLUDED"
                    : "ROLLOUT_EXCLUDED";

            return new EvaluateFlagResponse(
                    flagKey, userId, enabled, reason
            );
        });
    }


    private int calculateBucket(String flagKey, String userId) {

        try {
            String input = flagKey + ":" + userId;

            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    input.getBytes(StandardCharsets.UTF_8)
            );

            long hashValue = ByteBuffer.wrap(hash).getInt() & 0xffffffffL;

            return (int) (hashValue % 100);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 algorithm not available", e
            );
        }
    }
}
