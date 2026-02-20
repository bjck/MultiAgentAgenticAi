package com.bko.orchestration.service;

import static com.bko.orchestration.OrchestrationConstants.*;
import com.bko.config.AgentSkill;
import com.bko.config.MultiAgentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrchestrationPromptService {

    private final MultiAgentProperties properties;
    private final OrchestrationContextService contextService;

    public String roleSelectionPrompt(boolean requiresEdits, List<String> allowedRoles) {
        String basePrompt = ROLE_SELECTION_SYSTEM_PROMPT.formatted(String.join(", ", allowedRoles));
        if (requiresEdits) {
            basePrompt += ROLE_SELECTION_EDITS_INSTRUCTION;
        }
        return appendWorkspaceContext(basePrompt);
    }

    public String orchestratorSystemPrompt(boolean requiresEdits, List<String> allowedRoles, String registry) {
        String basePrompt = ORCHESTRATOR_SYSTEM_PROMPT.formatted(properties.getMaxTasks(), String.join(", ", allowedRoles), registry);
        if (requiresEdits) {
            basePrompt = basePrompt + ORCHESTRATOR_EDITS_INSTRUCTION;
        }
        basePrompt = appendSkillsToPrompt(basePrompt, properties.getSkills().getOrchestrator());
        return appendWorkspaceContext(basePrompt);
    }

    public String executionReviewPrompt(boolean requiresEdits, List<String> allowedRoles) {
        String basePrompt = EXECUTION_REVIEW_SYSTEM_PROMPT.formatted(String.join(", ", allowedRoles));
        if (requiresEdits) {
            basePrompt += EXECUTION_REVIEW_EDITS_INSTRUCTION;
        }
        return appendWorkspaceContext(basePrompt);
    }

    public String workerSystemPrompt(String role, boolean requiresEdits) {
        String basePrompt = WORKER_SYSTEM_PROMPT.formatted(role);
        if (requiresEdits) {
            basePrompt = basePrompt + WORKER_EDITS_INSTRUCTION;
        }
        List<AgentSkill> skills = properties.getSkills().getSkillsForWorkerRole(role);
        basePrompt = appendSkillsToPrompt(basePrompt, skills);
        return appendWorkspaceContext(basePrompt);
    }

    public String synthesisSystemPrompt() {
        String basePrompt = appendSkillsToPrompt(SYNTHESIS_SYSTEM_PROMPT, properties.getSkills().getSynthesis());
        return appendWorkspaceContext(basePrompt);
    }

    public String collaborationSystemPrompt(String role) {
        String basePrompt = COLLABORATION_SYSTEM_PROMPT.formatted(role);
        List<AgentSkill> skills = properties.getSkills().getSkillsForWorkerRole(role);
        basePrompt = appendSkillsToPrompt(basePrompt, skills);
        return appendWorkspaceContext(basePrompt);
    }

    private String appendWorkspaceContext(String basePrompt) {
        return basePrompt + "\n\n" + contextService.buildWorkspaceContext();
    }

    private String appendSkillsToPrompt(String basePrompt, List<AgentSkill> skills) {
        if (skills == null || skills.isEmpty()) {
            return basePrompt;
        }
        StringBuilder sb = new StringBuilder(basePrompt);
        sb.append("\n\nYou have the following skills:\n");
        for (AgentSkill skill : skills) {
            sb.append("\n### ").append(skill.getName());
            if (skill.getDescription() != null && !skill.getDescription().isBlank()) {
                sb.append("\n").append(skill.getDescription());
            }
            if (skill.getInstructions() != null && !skill.getInstructions().isBlank()) {
                sb.append("\nInstructions: ").append(skill.getInstructions());
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
