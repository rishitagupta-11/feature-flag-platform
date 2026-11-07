package com.featureflag.core.flag.dto;

import jakarta.validation.constraints.NotBlank;

public record EvaluateFlagRequest(
        @NotBlank String flagKey,
        @NotBlank String userId
) {}