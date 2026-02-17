package com.bko.orchestration.model;

import java.util.List;

public record OrchestratorPlan(
        String objective,
        List<TaskSpec> tasks
) {
}
