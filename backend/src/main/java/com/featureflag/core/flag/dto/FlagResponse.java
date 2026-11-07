
package com.featureflag.core.flag.dto;

import java.time.Instant;
import java.util.UUID;

public record FlagResponse(
        UUID id,
        String flagKey,
        String name,
        String description,
        boolean enabled,
        int rolloutPercentage,
        Instant createdAt,
        Instant updatedAt
) {}
