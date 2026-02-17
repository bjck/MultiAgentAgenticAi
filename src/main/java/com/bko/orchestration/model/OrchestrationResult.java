package com.bko.orchestration.model;

import java.util.List;

public record OrchestrationResult(
        OrchestratorPlan plan,
        List<WorkerResult> workerResults,
        String finalAnswer
) {
}
