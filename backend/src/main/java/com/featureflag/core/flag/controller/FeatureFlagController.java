
package com.featureflag.core.flag.controller;

import com.featureflag.core.flag.dto.CreateFlagRequest;
import com.featureflag.core.flag.dto.FlagResponse;
import com.featureflag.core.flag.dto.UpdateFlagRequest;
import com.featureflag.core.flag.dto.EvaluateFlagRequest;
import com.featureflag.core.flag.dto.EvaluateFlagResponse;
import com.featureflag.core.flag.entity.FeatureFlag;
import com.featureflag.core.flag.service.FeatureFlagService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/flags")
public class FeatureFlagController {

    private final FeatureFlagService service;

    public FeatureFlagController(FeatureFlagService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<FlagResponse> createFlag(
            @Valid @RequestBody CreateFlagRequest request) {

        FeatureFlag flag = service.createFlag(
                request.flagKey(),
                request.name(),
                request.description(),
                request.rolloutPercentage()
        );

        FlagResponse response = new FlagResponse(
                flag.getId(),
                flag.getFlagKey(),
                flag.getName(),
                flag.getDescription(),
                flag.isEnabled(),
                flag.getRolloutPercentage(),
                flag.getCreatedAt(),
                flag.getUpdatedAt()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<FlagResponse>> getAllFlags() {

        List<FlagResponse> flags = service.getAllFlags()
                .stream()
                .map(flag -> new FlagResponse(
                        flag.getId(),
                        flag.getFlagKey(),
                        flag.getName(),
                        flag.getDescription(),
                        flag.isEnabled(),
                        flag.getRolloutPercentage(),
                        flag.getCreatedAt(),
                        flag.getUpdatedAt()
                ))
                .toList();

        return ResponseEntity.ok(flags);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FlagResponse> getFlagById(
            @PathVariable UUID id) {

        FeatureFlag flag = service.getFlagById(id);

        FlagResponse response = new FlagResponse(
                flag.getId(),
                flag.getFlagKey(),
                flag.getName(),
                flag.getDescription(),
                flag.isEnabled(),
                flag.getRolloutPercentage(),
                flag.getCreatedAt(),
                flag.getUpdatedAt()
        );

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<FlagResponse> updateFlag(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateFlagRequest request) {

        FeatureFlag flag = service.updateFlag(
                id,
                request.name(),
                request.description(),
                request.enabled(),
                request.rolloutPercentage()
        );

        FlagResponse response = new FlagResponse(
                flag.getId(),
                flag.getFlagKey(),
                flag.getName(),
                flag.getDescription(),
                flag.isEnabled(),
                flag.getRolloutPercentage(),
                flag.getCreatedAt(),
                flag.getUpdatedAt()
        );

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFlag(@PathVariable UUID id) {

        service.deleteFlag(id);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/evaluate")
    public ResponseEntity<EvaluateFlagResponse> evaluateFlag(
            @Valid @RequestBody EvaluateFlagRequest request) {

        EvaluateFlagResponse response = service.evaluateFlag(
                request.flagKey(),
                request.userId()
        );

        return ResponseEntity.ok(response);
    }
}
