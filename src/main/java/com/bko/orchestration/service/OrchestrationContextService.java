package com.bko.orchestration.service;

import static com.bko.orchestration.OrchestrationConstants.ADVISORY_ROLES;
import com.bko.config.AgentSkill;
import com.bko.config.MultiAgentProperties;
import com.bko.orchestration.model.WorkerResult;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrchestrationContextService {

    private final MultiAgentProperties properties;

    public String buildRoleRegistry(List<String> roles) {
        StringBuilder sb = new StringBuilder();
        for (String role : roles) {
            List<AgentSkill> skills = properties.getSkills().getSkillsForWorkerRole(role);
            sb.append("- ").append(role).append("\n");
            if (skills == null || skills.isEmpty()) {
                sb.append("  skills: none\n");
                continue;
            }
            for (AgentSkill skill : skills) {
                sb.append("  - ").append(skill.getName());
                if (StringUtils.hasText(skill.getDescription())) {
                    sb.append(": ").append(skill.getDescription().trim());
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    public String buildAdvisoryContext(List<WorkerResult> results) {
        return buildResultsContext(filterResultsByRole(results, ADVISORY_ROLES));
    }

    public List<WorkerResult> filterResultsByRole(List<WorkerResult> results, Set<String> roles) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        return results.stream()
                .filter(result -> roles.contains(result.role()))
                .toList();
    }

    public String buildResultsContext(List<WorkerResult> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (WorkerResult result : results) {
            sb.append("[").append(result.role()).append(" - ").append(result.taskId()).append("]\n");
            sb.append(result.output()).append("\n\n");
        }
        return sb.toString().trim();
    }

    public String mergeContexts(String base, String addition) {
        if (!StringUtils.hasText(base)) {
            return StringUtils.hasText(addition) ? addition : "";
        }
        if (!StringUtils.hasText(addition)) {
            return base;
        }
        return base + "\n\n" + addition;
    }

    public String defaultContext(@Nullable String context) {
        return StringUtils.hasText(context) ? context : "None.";
    }
}
