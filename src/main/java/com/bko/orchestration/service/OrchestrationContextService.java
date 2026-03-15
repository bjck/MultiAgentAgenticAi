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
    private final WorkerSkillLibraryService workerSkillLibraryService;

    public String buildRoleRegistry(List<String> roles) {
        StringBuilder sb = new StringBuilder();
        for (String role : roles) {
            List<AgentSkill> skills = workerSkillLibraryService.skillsForWorkerRole(role);
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
        List<WorkerResult> deduped = dedupeLatestByTask(results);
        StringBuilder sb = new StringBuilder();
        for (WorkerResult result : deduped) {
            sb.append("[").append(result.role()).append(" - ").append(result.taskId()).append("]\n");
            sb.append(result.output()).append("\n\n");
        }
        return sb.toString().trim();
    }

    private List<WorkerResult> dedupeLatestByTask(List<WorkerResult> results) {
        java.util.LinkedHashMap<String, WorkerResult> map = new java.util.LinkedHashMap<>();
        for (WorkerResult result : results) {
            String key = result.taskId();
            if (map.containsKey(key)) {
                map.remove(key);
            }
            map.put(key, result);
        }
        return new java.util.ArrayList<>(map.values());
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

    public String buildWorkspaceContext() {
        String root = properties.getWorkspaceRoot();
        String rootDescription = StringUtils.hasText(root) ? root : "the current working directory";
        return """
                Workspace filesystem rules:

                - File tools (list_directory, read_file, write_file) operate only within the workspace root: %s
                - Never use absolute paths like "/" or "/home/...".
                - Always use relative paths such as ".", "src/main/java", "frontend/src", or other paths under the workspace root.
                - Treat the workspace root as your top-level folder for all file operations.
                """.formatted(rootDescription);
    }
}
