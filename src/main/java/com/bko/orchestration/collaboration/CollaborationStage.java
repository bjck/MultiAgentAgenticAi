package com.bko.orchestration.collaboration;

import org.springframework.util.StringUtils;

public record CollaborationStage(
        String key,
        String label,
        String expectedOutputTemplate,
        String summaryInstruction,
        boolean allowEdits
) {
    public String expectedOutput(String taskId, String baseExpectedOutput) {
        if (!StringUtils.hasText(expectedOutputTemplate)) {
            return baseExpectedOutput;
        }
        if (expectedOutputTemplate.contains("%s")) {
            return expectedOutputTemplate.formatted(taskId);
        }
        return expectedOutputTemplate;
    }
}
