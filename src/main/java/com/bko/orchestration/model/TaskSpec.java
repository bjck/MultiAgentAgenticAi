package com.bko.orchestration.model;

public record TaskSpec(
        String id,
        String role,
        String description,
        String expectedOutput
) {
}
