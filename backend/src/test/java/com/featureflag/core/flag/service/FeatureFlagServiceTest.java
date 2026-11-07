
package com.featureflag.core.flag.service;

import com.featureflag.core.flag.dto.EvaluateFlagResponse;
import com.featureflag.core.flag.entity.FeatureFlag;
import com.featureflag.core.flag.repository.FeatureFlagRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeatureFlagServiceTest {

    @Mock
    private FeatureFlagRepository repository;

    @InjectMocks
    private FeatureFlagService service;

    private FeatureFlag createFlag(
            boolean enabled,
            int rolloutPercentage) {

        FeatureFlag flag = new FeatureFlag();
        flag.setFlagKey("new_checkout");
        flag.setEnabled(enabled);
        flag.setRolloutPercentage(rolloutPercentage);

        return flag;
    }

    // TEST 1: Disabled flag must always return false
    @Test
    void shouldReturnFalseWhenFlagIsDisabled() {

        FeatureFlag flag = createFlag(false, 100);

        when(repository.findByFlagKey("new_checkout"))
                .thenReturn(Optional.of(flag));

        EvaluateFlagResponse response =
                service.evaluateFlag("new_checkout", "user_123");

        assertFalse(response.enabled());
        assertEquals("FLAG_DISABLED", response.reason());
    }

    // TEST 2: Non-existent flag must return false
    @Test
    void shouldReturnFalseWhenFlagDoesNotExist() {

        when(repository.findByFlagKey("new_checkout"))
                .thenReturn(Optional.empty());

        EvaluateFlagResponse response =
                service.evaluateFlag("new_checkout", "user_123");

        assertFalse(response.enabled());
        assertEquals("FLAG_NOT_FOUND", response.reason());
    }

    // TEST 3: Zero percent rollout must exclude everyone
    @Test
    void shouldExcludeEveryUserWhenRolloutIsZero() {

        FeatureFlag flag = createFlag(true, 0);

        when(repository.findByFlagKey("new_checkout"))
                .thenReturn(Optional.of(flag));

        for (int i = 1; i <= 100; i++) {

            EvaluateFlagResponse response =
                    service.evaluateFlag(
                            "new_checkout",
                            "user_" + i
                    );

            assertFalse(response.enabled());
            assertEquals("ROLLOUT_EXCLUDED", response.reason());
        }
    }

    // TEST 4: 100 percent rollout must include everyone
    @Test
    void shouldIncludeEveryUserWhenRolloutIs100() {

        FeatureFlag flag = createFlag(true, 100);

        when(repository.findByFlagKey("new_checkout"))
                .thenReturn(Optional.of(flag));

        for (int i = 1; i <= 100; i++) {

            EvaluateFlagResponse response =
                    service.evaluateFlag(
                            "new_checkout",
                            "user_" + i
                    );

            assertTrue(response.enabled());
            assertEquals("ROLLOUT_INCLUDED", response.reason());
        }
    }

    // TEST 5: Same user must always get the same result
    @Test
    void shouldReturnConsistentResultForSameUser() {

        FeatureFlag flag = createFlag(true, 25);

        when(repository.findByFlagKey("new_checkout"))
                .thenReturn(Optional.of(flag));

        EvaluateFlagResponse first =
                service.evaluateFlag("new_checkout", "user_123");

        EvaluateFlagResponse second =
                service.evaluateFlag("new_checkout", "user_123");

        EvaluateFlagResponse third =
                service.evaluateFlag("new_checkout", "user_123");

        assertEquals(first, second);
        assertEquals(second, third);
    }

    // TEST 6: 25 percent rollout should include approximately 25% of users
    @Test
    void shouldDistributeUsersApproximatelyAccordingToRolloutPercentage() {

        FeatureFlag flag = createFlag(true, 25);

        when(repository.findByFlagKey("new_checkout"))
                .thenReturn(Optional.of(flag));

        int totalUsers = 10_000;
        int enabledUsers = 0;

        for (int i = 1; i <= totalUsers; i++) {

            EvaluateFlagResponse response =
                    service.evaluateFlag(
                            "new_checkout",
                            "user_" + i
                    );

            if (response.enabled()) {
                enabledUsers++;
            }
        }

        double actualPercentage =
                (enabledUsers * 100.0) / totalUsers;

        assertTrue(
                actualPercentage >= 22.0
                        && actualPercentage <= 28.0,
                "Expected approximately 25% rollout, but got "
                        + actualPercentage + "%"
        );
    }
}
