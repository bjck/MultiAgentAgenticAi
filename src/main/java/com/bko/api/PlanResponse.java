package com.bko.api;

import com.bko.orchestration.model.OrchestratorPlan;
import com.bko.orchestration.model.TaskSpec;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlanResponse(
        String requestId,
        Instant createdAt,
        String objective,
        List<TaskSpec> tasks
) {

    public static PlanResponse from(OrchestratorPlan plan) {
        return new PlanResponse(
                UUID.randomUUID().toString(),
                Instant.now(),
                plan.objective(),
                plan.tasks()
        );
    }
}
