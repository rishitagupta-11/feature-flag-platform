
package com.featureflag.core.flag.repository;

import com.featureflag.core.flag.entity.FeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FeatureFlagRepository
        extends JpaRepository<FeatureFlag, UUID> {

    Optional<FeatureFlag> findByFlagKey(String flagKey);

    boolean existsByFlagKey(String flagKey);
}
