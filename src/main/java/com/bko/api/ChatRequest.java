package com.bko.api;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        @NotBlank String message,
        String provider,
        String model
) {
}
