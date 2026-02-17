package com.bko.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for agent skills.
 * Allows defining skills separately for the orchestrator, each worker role, and synthesis agent.
 */
public class AgentSkillsConfig {

    /**
     * Skills for the orchestrator agent (planning phase).
     */
    private List<AgentSkill> orchestrator = new ArrayList<>();

    /**
     * Skills for the synthesis agent (combining worker outputs).
     */
    private List<AgentSkill> synthesis = new ArrayList<>();

    /**
     * Skills for worker agents, keyed by role name (e.g., "research", "engineering").
     * Each role can have its own set of skills.
     */
    private Map<String, List<AgentSkill>> workers = new HashMap<>();

    /**
     * Default skills applied to all worker agents regardless of role.
     */
    private List<AgentSkill> workerDefaults = new ArrayList<>();

    public AgentSkillsConfig() {
    }

    public List<AgentSkill> getOrchestrator() {
        return orchestrator;
    }

    public void setOrchestrator(List<AgentSkill> orchestrator) {
        this.orchestrator = orchestrator != null ? orchestrator : new ArrayList<>();
    }

    public List<AgentSkill> getSynthesis() {
        return synthesis;
    }

    public void setSynthesis(List<AgentSkill> synthesis) {
        this.synthesis = synthesis != null ? synthesis : new ArrayList<>();
    }

    public Map<String, List<AgentSkill>> getWorkers() {
        return workers;
    }

    public void setWorkers(Map<String, List<AgentSkill>> workers) {
        this.workers = workers != null ? workers : new HashMap<>();
    }

    public List<AgentSkill> getWorkerDefaults() {
        return workerDefaults;
    }

    public void setWorkerDefaults(List<AgentSkill> workerDefaults) {
        this.workerDefaults = workerDefaults != null ? workerDefaults : new ArrayList<>();
    }

    /**
     * Get all skills for a specific worker role, combining role-specific skills with defaults.
     *
     * @param role the worker role
     * @return combined list of skills (defaults + role-specific)
     */
    public List<AgentSkill> getSkillsForWorkerRole(String role) {
        List<AgentSkill> combined = new ArrayList<>(workerDefaults);
        if (role != null && workers.containsKey(role.toLowerCase())) {
            combined.addAll(workers.get(role.toLowerCase()));
        }
        return combined;
    }
}
