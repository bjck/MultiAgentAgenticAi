package com.bko.orchestration.model;

import java.util.List;

public record PlanDraft(
        String planId,
        String sessionId,
        OrchestratorPlan plan,
        List<WorkerResult> findings,
        String status
) {
}
