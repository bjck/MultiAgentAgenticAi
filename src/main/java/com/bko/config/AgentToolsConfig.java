package com.bko.config;

import java.util.*;

/**
 * Configuration for attaching tool access to agents by phase and role.
 * This lets you restrict which tools are exposed to the LLM to reduce token usage
 * and to enforce least-privilege (e.g., analysis/orchestrator have no write tools).
 */
public class AgentToolsConfig {

    /**
     * Tool names allowed for the orchestrator/planning phase (applies to role selection and planning prompts).
     */
    private List<String> orchestrator = new ArrayList<>();

    /**
     * Tool names allowed for the synthesis phase (final aggregation/answer).
     */
    private List<String> synthesis = new ArrayList<>();

    /**
     * Default tool names for all worker roles unless overridden in workers map.
     */
    private List<String> workerDefaults = new ArrayList<>();

    /**
     * Tool names for specific worker roles (keyed by normalized role name).
     */
    private Map<String, List<String>> workers = new HashMap<>();

    public AgentToolsConfig() {}

    public List<String> getOrchestrator() { return orchestrator; }
    public void setOrchestrator(List<String> orchestrator) { this.orchestrator = orchestrator != null ? orchestrator : new ArrayList<>(); }

    public List<String> getSynthesis() { return synthesis; }
    public void setSynthesis(List<String> synthesis) { this.synthesis = synthesis != null ? synthesis : new ArrayList<>(); }

    public List<String> getWorkerDefaults() { return workerDefaults; }
    public void setWorkerDefaults(List<String> workerDefaults) { this.workerDefaults = workerDefaults != null ? workerDefaults : new ArrayList<>(); }

    public Map<String, List<String>> getWorkers() { return workers; }
    public void setWorkers(Map<String, List<String>> workers) { this.workers = workers != null ? workers : new HashMap<>(); }

    /**
     * Resolve allowed tools for a given worker role by combining defaults with role-specific entries.
     */
    public List<String> getToolsForWorkerRole(String role) {
        List<String> combined = new ArrayList<>(workerDefaults);
        if (role != null && workers.containsKey(role.toLowerCase(Locale.ROOT))) {
            combined.addAll(workers.get(role.toLowerCase(Locale.ROOT)));
        }
        // ensure distinct order-preserving
        return combined.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT)).distinct().toList();
    }
}
