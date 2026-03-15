package com.bko.orchestration.model;

import java.util.List;

public record SkillSelection(
        List<String> skills,
        String reason
) {
}
