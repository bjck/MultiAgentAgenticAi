package com.bko.orchestration.service;

import static com.bko.orchestration.OrchestrationConstants.ADVISORY_ROLES;
import com.bko.config.AgentSkill;
import com.bko.config.MultiAgentProperties;
import com.bko.orchestration.model.WorkerResult;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    public String buildWorkspaceContext() {
        String configuredRoot = properties.getWorkspaceRoot();
        String rootValue = StringUtils.hasText(configuredRoot)
                ? configuredRoot
                : System.getProperty("user.dir");
        Path root = Paths.get(rootValue).toAbsolutePath().normalize();

        StringBuilder sb = new StringBuilder();
        sb.append("Current Workspace Structure (").append(root.getFileName()).append("):\n");
        try {
            List<String> tree = generateFileTree(root, root, 0, 3);
            if (tree.isEmpty()) {
                sb.append(" (Empty or inaccessible)");
            } else {
                for (String line : tree) {
                    sb.append(line).append("\n");
                }
            }
        } catch (Exception e) {
            sb.append(" (Error listing workspace: ").append(e.getMessage()).append(")");
        }
        return sb.toString();
    }

    private List<String> generateFileTree(Path root, Path current, int depth, int maxDepth) throws IOException {
        if (depth > maxDepth) {
            return List.of();
        }

        try (Stream<Path> stream = Files.list(current)) {
            return stream
                    .filter(p -> !isIgnored(p))
                    .sorted((p1, p2) -> {
                        boolean d1 = Files.isDirectory(p1);
                        boolean d2 = Files.isDirectory(p2);
                        if (d1 != d2) return d1 ? -1 : 1;
                        return p1.getFileName().compareTo(p2.getFileName());
                    })
                    .flatMap(p -> {
                        String indent = "  ".repeat(depth);
                        String name = p.getFileName().toString();
                        if (Files.isDirectory(p)) {
                            Stream<String> dirLine = Stream.of(indent + "├─ " + name + "/");
                            try {
                                return Stream.concat(dirLine, generateFileTree(root, p, depth + 1, maxDepth).stream());
                            } catch (IOException e) {
                                return dirLine;
                            }
                        } else {
                            return Stream.of(indent + "├─ " + name);
                        }
                    })
                    .collect(Collectors.toList());
        }
    }

    private boolean isIgnored(Path path) {
        String name = path.getFileName().toString();
        return name.startsWith(".") || name.equals("node_modules") || name.equals("target") || name.equals("bin") || name.equals("build");
    }
}
