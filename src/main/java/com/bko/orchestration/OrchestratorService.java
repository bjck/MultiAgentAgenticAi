package com.bko.orchestration;

import com.bko.config.MultiAgentProperties;
import com.bko.orchestration.model.OrchestrationResult;
import com.bko.orchestration.model.OrchestratorPlan;
import com.bko.orchestration.model.TaskSpec;
import com.bko.orchestration.model.WorkerResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@Service
public class OrchestratorService {

    private static final String ORCHESTRATOR_USER_TEMPLATE = """
            User request:
            {input}
            """;

    private static final String WORKER_USER_TEMPLATE = """
            User request:
            {input}

            Assigned task:
            {task}

            Expected output:
            {expectedOutput}
            """;

    private static final String SYNTHESIS_USER_TEMPLATE = """
            User request:
            {input}

            Plan:
            {plan}

            Worker outputs:
            {results}
            """;

    private final ChatClient chatClient;
    private final MultiAgentProperties properties;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;
    private final ToolCallbackProvider toolCallbackProvider;

    public OrchestratorService(ChatClient.Builder builder,
                               MultiAgentProperties properties,
                               ObjectMapper objectMapper,
                               @Nullable ToolCallbackProvider toolCallbackProvider) {
        this.chatClient = builder.build();
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.executor = Executors.newFixedThreadPool(properties.getWorkerConcurrency());
        this.toolCallbackProvider = toolCallbackProvider;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    public OrchestrationResult orchestrate(String userMessage) {
        OrchestratorPlan plan = requestPlan(userMessage);
        List<TaskSpec> tasks = plan.tasks();
        List<WorkerResult> results = executeTasks(userMessage, tasks);
        String finalAnswer = synthesize(userMessage, plan, results);
        return new OrchestrationResult(plan, results, finalAnswer);
    }

    public OrchestratorPlan plan(String userMessage) {
        return requestPlan(userMessage);
    }

    private OrchestratorPlan requestPlan(String userMessage) {
        try {
            String systemPrompt = orchestratorSystemPrompt();
            OrchestratorPlan plan = applyTools(chatClient.prompt())
                    .system(systemPrompt)
                    .user(user -> user.text(ORCHESTRATOR_USER_TEMPLATE).param("input", userMessage))
                    .call()
                    .entity(OrchestratorPlan.class);
            return sanitizePlan(plan, userMessage);
        } catch (Exception ex) {
            return defaultPlan(userMessage);
        }
    }

    private List<WorkerResult> executeTasks(String userMessage, List<TaskSpec> tasks) {
        Duration timeout = properties.getWorkerTimeout();
        List<CompletableFuture<WorkerResult>> futures = tasks.stream()
                .map(task -> CompletableFuture.supplyAsync(() -> runWorker(userMessage, task), executor)
                        .orTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                        .exceptionally(ex -> new WorkerResult(task.id(), task.role(),
                                "Worker failed: " + ex.getMessage())))
                .toList();
        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    private WorkerResult runWorker(String userMessage, TaskSpec task) {
        String systemPrompt = workerSystemPrompt(task.role());
        String output = applyTools(chatClient.prompt())
                .system(systemPrompt)
                .user(user -> user.text(WORKER_USER_TEMPLATE)
                        .param("input", userMessage)
                        .param("task", task.description())
                        .param("expectedOutput", task.expectedOutput()))
                .call()
                .content();
        return new WorkerResult(task.id(), task.role(), output);
    }

    private String synthesize(String userMessage, OrchestratorPlan plan, List<WorkerResult> results) {
        if (results.size() == 1) {
            return results.getFirst().output();
        }
        String planJson = toJson(plan);
        String resultsJson = toJson(results);
        return applyTools(chatClient.prompt())
                .system(synthesisSystemPrompt())
                .user(user -> user.text(SYNTHESIS_USER_TEMPLATE)
                        .param("input", userMessage)
                        .param("plan", planJson)
                        .param("results", resultsJson))
                .call()
                .content();
    }

    private OrchestratorPlan sanitizePlan(OrchestratorPlan plan, String userMessage) {
        if (plan == null || plan.tasks() == null || plan.tasks().isEmpty()) {
            return defaultPlan(userMessage);
        }
        String objective = StringUtils.hasText(plan.objective()) ? plan.objective() : userMessage;
        List<TaskSpec> incomingTasks = plan.tasks();
        int maxTasks = Math.min(properties.getMaxTasks(), incomingTasks.size());
        List<TaskSpec> sanitized = new ArrayList<>(maxTasks);
        List<String> allowedRoles = normalizedRoles();
        IntStream.range(0, maxTasks).forEach(index -> {
            TaskSpec task = incomingTasks.get(index);
            String id = StringUtils.hasText(task.id()) ? task.id() : "task-" + (index + 1);
            String role = normalizeRole(task.role(), allowedRoles);
            String description = StringUtils.hasText(task.description()) ? task.description() : userMessage;
            String expectedOutput = StringUtils.hasText(task.expectedOutput())
                    ? task.expectedOutput()
                    : "Provide concise, actionable output.";
            sanitized.add(new TaskSpec(id, role, description, expectedOutput));
        });
        return new OrchestratorPlan(objective, sanitized);
    }

    private OrchestratorPlan defaultPlan(String userMessage) {
        TaskSpec fallback = new TaskSpec("task-1", "general", userMessage,
                "Provide a complete response to the user request.");
        return new OrchestratorPlan(userMessage, List.of(fallback));
    }

    private String orchestratorSystemPrompt() {
        return """
                You are the Orchestrator agent. Break the user's request into up to %d parallel tasks.
                Assign each task a role from: %s.
                Keep tasks independent and specific. Each task should be actionable by a single worker.
                You may use MCP filesystem tools to inspect the workspace when needed. Do not modify files unless requested.
                Return only JSON that matches the requested schema.
                """.formatted(properties.getMaxTasks(), String.join(", ", properties.getWorkerRoles()));
    }

    private String workerSystemPrompt(String role) {
        return """
                You are a %s worker agent.
                Focus only on the assigned task. Be concise and practical.
                You may use MCP filesystem tools to read or list files in the workspace. Only write files when explicitly instructed.
                If assumptions are required, list them explicitly.
                """.formatted(role);
    }

    private String synthesisSystemPrompt() {
        return """
                You are the synthesis agent. Combine worker outputs into a single, coherent response.
                Resolve conflicts, remove duplication, and answer the user's request directly.
                If MCP tool output was used, summarize relevant file changes accurately.
                """;
    }

    private String normalizeRole(String role, List<String> allowedRoles) {
        if (!StringUtils.hasText(role)) {
            return "general";
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        return allowedRoles.contains(normalized) ? normalized : "general";
    }

    private List<String> normalizedRoles() {
        return properties.getWorkerRoles().stream()
                .filter(StringUtils::hasText)
                .map(role -> role.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private ChatClient.ChatClientRequestSpec applyTools(ChatClient.ChatClientRequestSpec prompt) {
        if (toolCallbackProvider == null) {
            return prompt;
        }
        return prompt.toolCallbacks(toolCallbackProvider);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "\"serialization-failed-" + UUID.randomUUID() + "\"";
        }
    }
}
