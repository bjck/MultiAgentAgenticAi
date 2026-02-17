package com.bko.orchestration.model;

public record WorkerResult(
        String taskId,
        String role,
        String output
) {
}
