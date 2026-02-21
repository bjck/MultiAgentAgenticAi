package com.bko.api;

import com.bko.orchestration.model.TaskSpec;
import com.bko.orchestration.model.PlanDraft;
import com.bko.orchestration.model.WorkerResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlanResponse(
        String requestId,
        Instant createdAt,
        String sessionId,
        String planId,
        String status,
        String objective,
        List<TaskSpec> tasks,
        List<WorkerResult> findings
) {

    public static PlanResponse from(PlanDraft draft) {
        return new PlanResponse(
                UUID.randomUUID().toString(),
                Instant.now(),
                draft.sessionId(),
                draft.planId(),
                draft.status(),
                draft.plan().objective(),
                draft.plan().tasks(),
                draft.findings()
        );
    }
}
