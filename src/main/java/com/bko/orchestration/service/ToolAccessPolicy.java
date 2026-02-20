package com.bko.orchestration.service;

import com.bko.config.AgentToolsConfig;
import com.bko.config.MultiAgentProperties;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static com.bko.orchestration.OrchestrationConstants.*;

/**
 * Decides which tool names are permitted for each agent phase/role.
 * Uses configuration (multiagent.tools) with sensible, least-privilege defaults.
 */
@Service
public class ToolAccessPolicy {

    public enum Phase { ORCHESTRATOR, WORKER, SYNTHESIS }

    private final MultiAgentProperties properties;

    public ToolAccessPolicy(MultiAgentProperties properties) {
        this.properties = properties;
    }

    /**
     * Compute allowed tool names for a given phase and optional worker role.
     */
    public List<String> allowedToolNames(Phase phase, String role) {
        AgentToolsConfig cfg = properties.getTools();
        List<String> names;
        switch (phase) {
            case ORCHESTRATOR -> names = normalize(cfg.getOrchestrator());
            case SYNTHESIS -> names = normalize(cfg.getSynthesis());
            case WORKER -> names = role == null ? normalize(cfg.getWorkerDefaults()) : cfg.getToolsForWorkerRole(role);
            default -> names = List.of();
        }
        if (!names.isEmpty()) {
            return names;
        }
        // Fallback defaults (least privilege)
        return defaultFor(phase, role);
    }

    private List<String> defaultFor(Phase phase, String role) {
        // Default to no tools for orchestrator & synthesis
        if (phase == Phase.ORCHESTRATOR || phase == Phase.SYNTHESIS) {
            return List.of();
        }
        // Worker defaults by role
        String normalizedRole = role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
        if (ROLE_IMPLEMENTER.equals(normalizedRole)) {
            return List.of("list_directory", "read_file", "write_file");
        }
        if (ROLE_ENGINEERING.equals(normalizedRole)) {
            return List.of("list_directory", "read_file");
        }
        if (ROLE_ANALYSIS.equals(normalizedRole)) {
            return List.of("list_directory", "read_file");
        }
        // Other roles: read-only filesystem by default
        return List.of("list_directory", "read_file");
    }

    private List<String> normalize(List<String> in) {
        if (in == null) return List.of();
        return in.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .distinct()
                .collect(Collectors.toList());
    }
}
