
package com.featureflag.core.flag.cache;

import java.util.UUID;

public record CachedFlag(
        UUID id,
        String flagKey,
        boolean enabled,
        int rolloutPercentage
) {}
