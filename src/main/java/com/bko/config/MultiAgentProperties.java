package com.bko.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "multiagent")
public class MultiAgentProperties {

    private int maxTasks = 4;
    private int workerConcurrency = 4;
    private Duration workerTimeout = Duration.ofSeconds(90);
    private List<String> workerRoles = new ArrayList<>(
            List.of("analysis", "research", "design", "engineering", "qa", "writing", "general"));
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
