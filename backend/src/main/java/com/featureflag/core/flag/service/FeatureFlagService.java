
package com.featureflag.core.flag.service;

import com.featureflag.core.flag.entity.FeatureFlag;
import com.featureflag.core.flag.repository.FeatureFlagRepository;
import com.featureflag.core.flag.dto.EvaluateFlagResponse;
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

    public FeatureFlagService(FeatureFlagRepository repository) {
        this.repository = repository;
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
                .orElseThrow(() -> new RuntimeException(
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
                .orElseThrow(() -> new RuntimeException(
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

        return repository.save(flag);
    }

    @Transactional
    public void deleteFlag(UUID id) {
        FeatureFlag flag = repository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Feature flag not found with id: " + id
                ));

        repository.delete(flag);
    }

    @Transactional(readOnly = true)
    public EvaluateFlagResponse evaluateFlag(String flagKey, String userId) {

        FeatureFlag flag = repository.findByFlagKey(flagKey)
                .orElse(null);

        if (flag == null) {
            return new EvaluateFlagResponse(
                    flagKey, userId, false, "FLAG_NOT_FOUND"
            );
        }

        if (!flag.isEnabled()) {
            return new EvaluateFlagResponse(
                    flagKey, userId, false, "FLAG_DISABLED"
            );
        }

        int bucket = calculateBucket(flagKey, userId);

        boolean enabled = bucket < flag.getRolloutPercentage();

        String reason = enabled
                ? "ROLLOUT_INCLUDED"
                : "ROLLOUT_EXCLUDED";

        return new EvaluateFlagResponse(
                flagKey, userId, enabled, reason
        );
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
