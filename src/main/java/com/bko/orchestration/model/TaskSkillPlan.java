package com.bko.orchestration.model;

import java.util.List;

public record TaskSkillPlan(
        String taskId,
        String role,
        int budget,
        List<SkillSummary> skills,
        String rationale
) {
}
