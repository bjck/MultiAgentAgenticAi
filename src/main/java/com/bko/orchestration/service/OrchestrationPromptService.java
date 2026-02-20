package com.bko.orchestration.service;

import static com.bko.orchestration.OrchestrationConstants.*;
import com.bko.config.AgentSkill;
import com.bko.config.MultiAgentProperties;
import com.bko.orchestration.collaboration.CollaborationStage;
import com.bko.orchestration.collaboration.CollaborationStrategy;
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

    public String workerSystemPrompt(String role, boolean requiresEdits, boolean includeHandoffSchema) {
        String basePrompt = WORKER_SYSTEM_PROMPT.formatted(role);
        if (requiresEdits) {
            basePrompt = basePrompt + WORKER_EDITS_INSTRUCTION;
        }
        List<AgentSkill> skills = properties.getSkills().getSkillsForWorkerRole(role);
        basePrompt = appendSkillsToPrompt(basePrompt, skills);
        if (includeHandoffSchema) {
            basePrompt = appendHandoffSchema(basePrompt, role);
        }
        return appendWorkspaceContext(basePrompt);
    }

    public String synthesisSystemPrompt() {
        String basePrompt = appendSkillsToPrompt(SYNTHESIS_SYSTEM_PROMPT, properties.getSkills().getSynthesis());
        return appendWorkspaceContext(basePrompt);
    }

    public String collaborationSystemPrompt(String role, CollaborationStrategy strategy, CollaborationStage stage, boolean finalStage) {
        String basePrompt = COLLABORATION_SYSTEM_PROMPT.formatted(role);
        if (strategy != null) {
            basePrompt = basePrompt + "\n\nStrategy: " + strategy.label();
        }
        if (stage != null && StringUtils.hasText(stage.summaryInstruction())) {
            basePrompt = basePrompt + "\n\n" + stage.summaryInstruction().trim();
        }
        List<AgentSkill> skills = properties.getSkills().getSkillsForWorkerRole(role);
        basePrompt = appendSkillsToPrompt(basePrompt, skills);
        if (finalStage) {
            basePrompt = appendFinalStageHandoffSchema(basePrompt, role);
        }
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

    private String appendHandoffSchema(String basePrompt, String role) {
        String schema = handoffSchemaForRole(role);
        if (!StringUtils.hasText(schema)) {
            return basePrompt;
        }
        return basePrompt + "\n\nReturn only JSON that matches this handoff schema:\n" + schema;
    }

    private String appendFinalStageHandoffSchema(String basePrompt, String role) {
        String schema = handoffSchemaForRole(role);
        if (!StringUtils.hasText(schema)) {
            return basePrompt;
        }
        return basePrompt + "\n\nFinal stage output must return only JSON that matches this handoff schema:\n" + schema;
    }

    private String handoffSchemaForRole(String role) {
        if (!StringUtils.hasText(role)) {
            return null;
        }
        String normalized = role.trim().toLowerCase();
        return switch (normalized) {
            case ROLE_ANALYSIS -> ANALYSIS_HANDOFF_SCHEMA;
            case ROLE_DESIGN -> DESIGN_HANDOFF_SCHEMA;
            case ROLE_ENGINEERING -> ENGINEERING_HANDOFF_SCHEMA;
            case ROLE_IMPLEMENTER -> IMPLEMENTER_HANDOFF_SCHEMA;
            default -> null;
        };
    }
}
