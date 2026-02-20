package com.bko.config;

import com.bko.orchestration.collaboration.CollaborationStrategy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "multiagent")
public class MultiAgentProperties {

    private int maxTasks = 4;
    private int workerConcurrency = 4;
    private Duration workerTimeout = Duration.ofSeconds(90);
    private RoleExecutionConfig roleExecutionDefaults = new RoleExecutionConfig();
    private java.util.Map<String, RoleExecutionConfig> roleExecution = new java.util.HashMap<>();
    private List<String> workerRoles = new ArrayList<>(
            List.of("analysis", "research", "design", "engineering", "implementer", "qa", "writing", "general"));
    private String workspaceRoot;
    private AgentSkillsConfig skills = new AgentSkillsConfig();
    private AgentToolsConfig tools = new AgentToolsConfig();
    private AiProvider aiProvider = AiProvider.GOOGLE;
    private OpenAIConfig openai = new OpenAIConfig();
    private GoogleConfig google = new GoogleConfig();

    public enum AiProvider {
        GOOGLE, OPENAI
    }

    public static class GoogleConfig {
        private String apiKey;
        private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }

    public static class OpenAIConfig {
        private String apiKey;
        private String baseUrl = "https://api-cortex.t-netcompany.com/v1";
        private String model = "claude-4-sonnet"; // Default from Postman

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }

    public AiProvider getAiProvider() {
        return aiProvider;
    }

    public void setAiProvider(AiProvider aiProvider) {
        this.aiProvider = aiProvider;
    }

    public OpenAIConfig getOpenai() {
        return openai;
    }

    public void setOpenai(OpenAIConfig openai) {
        this.openai = openai;
    }

    public GoogleConfig getGoogle() {
        return google;
    }

    public void setGoogle(GoogleConfig google) {
        this.google = google;
    }

    public int getMaxTasks() {
        return maxTasks;
    }

    public void setMaxTasks(int maxTasks) {
        this.maxTasks = maxTasks;
    }

    public int getWorkerConcurrency() {
        return workerConcurrency;
    }

    public void setWorkerConcurrency(int workerConcurrency) {
        this.workerConcurrency = workerConcurrency;
    }

    public RoleExecutionConfig getRoleExecutionDefaults() {
        return roleExecutionDefaults;
    }

    public void setRoleExecutionDefaults(RoleExecutionConfig roleExecutionDefaults) {
        this.roleExecutionDefaults = roleExecutionDefaults != null ? roleExecutionDefaults : new RoleExecutionConfig();
    }

    public java.util.Map<String, RoleExecutionConfig> getRoleExecution() {
        return roleExecution;
    }

    public void setRoleExecution(java.util.Map<String, RoleExecutionConfig> roleExecution) {
        if (roleExecution == null) {
            return;
        }
        this.roleExecution = new java.util.HashMap<>(roleExecution);
    }

    public RoleExecutionConfig getRoleExecutionConfig(String role) {
        RoleExecutionConfig base = roleExecutionDefaults != null ? roleExecutionDefaults : new RoleExecutionConfig();
        if (role == null) {
            return new RoleExecutionConfig(base.getRounds(), base.getAgents(), base.getCollaborationStrategy());
        }
        RoleExecutionConfig override = roleExecution.get(role.toLowerCase());
        if (override == null) {
            return new RoleExecutionConfig(base.getRounds(), base.getAgents(), base.getCollaborationStrategy());
        }
        int rounds = override.getRounds() > 0 ? override.getRounds() : base.getRounds();
        int agents = override.getAgents() > 0 ? override.getAgents() : base.getAgents();
        CollaborationStrategy strategy = override.getCollaborationStrategy() != null
                ? override.getCollaborationStrategy()
                : base.getCollaborationStrategy();
        return new RoleExecutionConfig(rounds, agents, strategy);
    }

    public static class RoleExecutionConfig {
        private int rounds = 1;
        private int agents = 1;
        private CollaborationStrategy collaborationStrategy = CollaborationStrategy.SIMPLE_SUMMARY;

        public RoleExecutionConfig() {}

        public RoleExecutionConfig(int rounds, int agents) {
            this.rounds = rounds;
            this.agents = agents;
        }

        public RoleExecutionConfig(int rounds, int agents, CollaborationStrategy collaborationStrategy) {
            this.rounds = rounds;
            this.agents = agents;
            this.collaborationStrategy = collaborationStrategy != null
                    ? collaborationStrategy
                    : CollaborationStrategy.SIMPLE_SUMMARY;
        }

        public int getRounds() {
            return rounds;
        }

        public void setRounds(int rounds) {
            this.rounds = rounds;
        }

        public int getAgents() {
            return agents;
        }

        public void setAgents(int agents) {
            this.agents = agents;
        }

        public CollaborationStrategy getCollaborationStrategy() {
            return collaborationStrategy;
        }

        public void setCollaborationStrategy(CollaborationStrategy collaborationStrategy) {
            if (collaborationStrategy == null) {
                return;
            }
            this.collaborationStrategy = collaborationStrategy;
        }
    }

    public Duration getWorkerTimeout() {
        return workerTimeout;
    }

    public void setWorkerTimeout(Duration workerTimeout) {
        this.workerTimeout = workerTimeout;
    }

    public List<String> getWorkerRoles() {
        return workerRoles;
    }

    public void setWorkerRoles(List<String> workerRoles) {
        if (workerRoles == null || workerRoles.isEmpty()) {
            return;
        }
        this.workerRoles = new ArrayList<>(workerRoles);
    }

    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    public void setWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }


    public AgentSkillsConfig getSkills() {
        return skills;
    }

    public void setSkills(AgentSkillsConfig skills) {
        this.skills = skills != null ? skills : new AgentSkillsConfig();
    }

    public AgentToolsConfig getTools() {
        return tools;
    }

    public void setTools(AgentToolsConfig tools) {
        this.tools = tools != null ? tools : new AgentToolsConfig();
    }
}
