package com.bko.api;

import jakarta.validation.constraints.NotBlank;

public record PlanExecuteRequest(
        @NotBlank String planId,
        String feedback,
        String provider,
        String model
) {
}
