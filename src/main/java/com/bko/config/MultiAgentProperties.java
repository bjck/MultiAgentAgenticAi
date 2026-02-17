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
            List.of("research", "design", "engineering", "qa", "writing", "general"));
    private String workspaceRoot;

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
}
