package com.bko.api;

import jakarta.validation.constraints.NotBlank;

public record CancelRunRequest(
        @NotBlank String runId
) {
}
