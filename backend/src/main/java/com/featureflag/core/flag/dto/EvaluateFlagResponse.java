package com.featureflag.core.flag.dto;

public record EvaluateFlagResponse(
        String flagKey,
        String userId,
        boolean enabled,
        String reason
) {}