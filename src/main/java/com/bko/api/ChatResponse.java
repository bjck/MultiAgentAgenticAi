package com.bko.api;

import com.bko.orchestration.model.OrchestrationResult;
import com.bko.orchestration.model.OrchestratorPlan;
import com.bko.orchestration.model.WorkerResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatResponse(
        String requestId,
        Instant createdAt,
        OrchestratorPlan plan,
        List<WorkerResult> workerResults,
        String finalAnswer
) {

    public static ChatResponse from(OrchestrationResult result) {
        return new ChatResponse(UUID.randomUUID().toString(), Instant.now(),
                result.plan(), result.workerResults(), result.finalAnswer());
    }
}
