package com.bko.api;

import com.bko.config.AgentSkill;
import com.bko.config.AgentSkillsConfig;
import com.bko.config.MultiAgentProperties;
import com.bko.config.MultiAgentProperties.RoleExecutionConfig;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final MultiAgentProperties properties;

    public ConfigController(MultiAgentProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/skills")
    public SkillsResponse getSkills() {
        AgentSkillsConfig skills = properties.getSkills();
        return new SkillsResponse(
                skills.getOrchestrator(),
                skills.getSynthesis(),
                skills.getWorkerDefaults(),
                skills.getWorkers(),
                properties.getWorkerRoles()
        );
    }

    @GetMapping("/role-settings")
    public RoleSettingsResponse getRoleSettings() {
        Map<String, RoleExecutionConfig> merged = new java.util.LinkedHashMap<>();
        for (String role : properties.getWorkerRoles()) {
            merged.put(role, properties.getRoleExecutionConfig(role));
        }
        return new RoleSettingsResponse(merged, properties.getRoleExecutionDefaults(), properties.getWorkerRoles());
    }

    @PutMapping("/role-settings")
    public RoleSettingsResponse updateRoleSettings(@RequestBody RoleSettingsRequest request) {
        if (request != null) {
            if (request.defaults() != null) {
                properties.setRoleExecutionDefaults(request.defaults());
            }
            if (request.roles() != null) {
                Map<String, RoleExecutionConfig> normalized = new java.util.HashMap<>();
                request.roles().forEach((key, value) -> {
                    if (key != null && value != null) {
                        normalized.put(key.toLowerCase(), value);
                    }
                });
                properties.setRoleExecution(normalized);
            }
        }
        return getRoleSettings();
    }

    @PutMapping("/skills/orchestrator")
    public List<AgentSkill> updateOrchestratorSkills(@RequestBody List<AgentSkill> skills) {
        properties.getSkills().setOrchestrator(skills);
        return properties.getSkills().getOrchestrator();
    }

    @PutMapping("/skills/synthesis")
    public List<AgentSkill> updateSynthesisSkills(@RequestBody List<AgentSkill> skills) {
        properties.getSkills().setSynthesis(skills);
        return properties.getSkills().getSynthesis();
    }

    @PutMapping("/skills/worker-defaults")
    public List<AgentSkill> updateWorkerDefaultSkills(@RequestBody List<AgentSkill> skills) {
        properties.getSkills().setWorkerDefaults(skills);
        return properties.getSkills().getWorkerDefaults();
    }

    @PutMapping("/skills/workers/{role}")
    public List<AgentSkill> updateWorkerRoleSkills(@PathVariable String role, @RequestBody List<AgentSkill> skills) {
        properties.getSkills().getWorkers().put(role.toLowerCase(), skills);
        return properties.getSkills().getWorkers().get(role.toLowerCase());
    }

    public record SkillsResponse(
            List<AgentSkill> orchestrator,
            List<AgentSkill> synthesis,
            List<AgentSkill> workerDefaults,
            Map<String, List<AgentSkill>> workers,
            List<String> workerRoles
    ) {}

    public record RoleSettingsRequest(
            RoleExecutionConfig defaults,
            Map<String, RoleExecutionConfig> roles
    ) {}

    public record RoleSettingsResponse(
            Map<String, RoleExecutionConfig> roles,
            RoleExecutionConfig defaults,
            List<String> workerRoles
    ) {}
}
