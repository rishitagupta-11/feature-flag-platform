
package com.featureflag.core.flag.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateFlagRequest(

        @NotBlank
        @Size(max = 100)
        String flagKey,

        @NotBlank
        @Size(max = 150)
        String name,

        @Size(max = 500)
        String description,

        @Min(0)
        @Max(100)
        int rolloutPercentage

) {}