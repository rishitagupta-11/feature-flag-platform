
package com.featureflag.core.flag.service;

import com.featureflag.core.flag.entity.FeatureFlag;
import com.featureflag.core.flag.repository.FeatureFlagRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FeatureFlagConcurrencyIntegrationTest {

    @Autowired
    private FeatureFlagRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void shouldPreventStaleConcurrentUpdate() {

        // Create a feature flag.
        FeatureFlag flag = new FeatureFlag();
        flag.setFlagKey("concurrency_" + UUID.randomUUID());
        flag.setName("Concurrency Test");
        flag.setEnabled(true);
        flag.setRolloutPercentage(20);

        flag = repository.saveAndFlush(flag);

        UUID flagId = flag.getId();

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        // Transaction A reads the original version.
        FeatureFlag adminA = transactionTemplate.execute(status ->
                repository.findById(flagId).orElseThrow()
        );

        // Transaction B reads the same original version.
        FeatureFlag adminB = transactionTemplate.execute(status ->
                repository.findById(flagId).orElseThrow()
        );

        // Both administrators modify their copies.
        adminA.setRolloutPercentage(50);
        adminB.setRolloutPercentage(80);

        // Admin A saves successfully.
        transactionTemplate.executeWithoutResult(status ->
                repository.saveAndFlush(adminA)
        );

        // Admin B must fail because its version is stale.
        assertThrows(RuntimeException.class, () ->
                transactionTemplate.executeWithoutResult(status ->
                        repository.saveAndFlush(adminB)
                )
        );

        // Verify that Admin A's update was preserved.
        FeatureFlag finalFlag = repository.findById(flagId).orElseThrow();

        assertEquals(50, finalFlag.getRolloutPercentage());

        repository.delete(finalFlag);
    }
}
