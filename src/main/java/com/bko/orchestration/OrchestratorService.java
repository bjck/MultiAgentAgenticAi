package com.bko.orchestration;

import com.bko.config.AgentSkill;
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
        boolean requiresEdits = requiresFileEdits(userMessage);
        OrchestratorPlan plan = requestPlan(userMessage, requiresEdits);
        List<TaskSpec> tasks = plan.tasks();
        List<WorkerResult> results = executeTasks(userMessage, tasks, requiresEdits);
        String finalAnswer = synthesize(userMessage, plan, results);
        return new OrchestrationResult(plan, results, finalAnswer);
    }

    public OrchestratorPlan plan(String userMessage) {
        return requestPlan(userMessage, requiresFileEdits(userMessage));
    }

    private OrchestratorPlan requestPlan(String userMessage, boolean requiresEdits) {
        try {
            String systemPrompt = orchestratorSystemPrompt(requiresEdits);
            OrchestratorPlan plan = applyTools(chatClient.prompt())
                    .system(systemPrompt)
                    .user(user -> user.text(ORCHESTRATOR_USER_TEMPLATE).param("input", userMessage))
                    .call()
                    .entity(OrchestratorPlan.class);
            return sanitizePlan(plan, userMessage, requiresEdits);
        } catch (Exception ex) {
            return defaultPlan(userMessage, requiresEdits);
        }
    }

    private List<WorkerResult> executeTasks(String userMessage, List<TaskSpec> tasks, boolean requiresEdits) {
        Duration timeout = properties.getWorkerTimeout();
        if (requiresEdits) {
            List<WorkerResult> results = new ArrayList<>(tasks.size());
            for (TaskSpec task : tasks) {
                WorkerResult result = CompletableFuture
                        .supplyAsync(() -> runWorker(userMessage, task, true), executor)
                        .orTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                        .exceptionally(ex -> new WorkerResult(task.id(), task.role(),
                                "Worker failed: " + ex.getMessage()))
                        .join();
                results.add(result);
            }
            return results;
        }
        List<CompletableFuture<WorkerResult>> futures = tasks.stream()
                .map(task -> CompletableFuture.supplyAsync(() -> runWorker(userMessage, task, false), executor)
                        .orTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                        .exceptionally(ex -> new WorkerResult(task.id(), task.role(),
                                "Worker failed: " + ex.getMessage())))
                .toList();
        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    private WorkerResult runWorker(String userMessage, TaskSpec task, boolean requiresEdits) {
        String systemPrompt = workerSystemPrompt(task.role(), requiresEdits);
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

    private OrchestratorPlan sanitizePlan(OrchestratorPlan plan, String userMessage, boolean requiresEdits) {
        if (plan == null || plan.tasks() == null || plan.tasks().isEmpty()) {
            return defaultPlan(userMessage, requiresEdits);
        }
        String objective = StringUtils.hasText(plan.objective()) ? plan.objective() : userMessage;
        List<TaskSpec> incomingTasks = plan.tasks();
        int maxTasks = Math.min(properties.getMaxTasks(), incomingTasks.size());
        List<TaskSpec> sanitized = new ArrayList<>(maxTasks);
        List<String> allowedRoles = normalizedRoles();
        String writerRole = preferredWriterRole(allowedRoles);
        final boolean[] writerAssigned = {false};
        IntStream.range(0, maxTasks).forEach(index -> {
            TaskSpec task = incomingTasks.get(index);
            String id = StringUtils.hasText(task.id()) ? task.id() : "task-" + (index + 1);
            String role = normalizeRole(task.role(), allowedRoles);
            String description = StringUtils.hasText(task.description()) ? task.description() : userMessage;
            String expectedOutput = StringUtils.hasText(task.expectedOutput())
                    ? task.expectedOutput()
                    : "Provide concise, actionable output.";
            if (requiresEdits) {
                if (!writerAssigned[0]) {
                    role = writerRole;
                    expectedOutput = appendFileEditInstruction(expectedOutput, true);
                    writerAssigned[0] = true;
                } else {
                    expectedOutput = appendFileEditInstruction(expectedOutput, false);
                }
            }
            sanitized.add(new TaskSpec(id, role, description, expectedOutput));
        });
        return new OrchestratorPlan(objective, sanitized);
    }

    private OrchestratorPlan defaultPlan(String userMessage, boolean requiresEdits) {
        String expectedOutput = "Provide a complete response to the user request.";
        if (requiresEdits) {
            expectedOutput = appendFileEditInstruction(expectedOutput, true);
        }
        String role = requiresEdits ? preferredWriterRole(normalizedRoles()) : "general";
        TaskSpec fallback = new TaskSpec("task-1", role, userMessage, expectedOutput);
        return new OrchestratorPlan(userMessage, List.of(fallback));
    }

    private String orchestratorSystemPrompt(boolean requiresEdits) {
        String basePrompt = """
                You are the Orchestrator agent. Break the user's request into up to %d parallel tasks.
                Assign each task a role from: %s.
                Keep tasks independent and specific. Each task should be actionable by a single worker.
                You may use MCP filesystem tools to inspect the workspace when needed.
                If the user requests code or content changes, ensure at least one task is explicitly responsible for applying file edits via MCP filesystem tools.
                Make it clear which task should write files and which should not.
                Return only JSON that matches the requested schema.
                """.formatted(properties.getMaxTasks(), String.join(", ", properties.getWorkerRoles()));
        if (requiresEdits) {
            basePrompt = basePrompt + """

                    The user request requires code changes. Ensure the plan includes one explicit implementation task that will modify files.
                    """;
        }
        return appendSkillsToPrompt(basePrompt, properties.getSkills().getOrchestrator());
    }

    private String workerSystemPrompt(String role, boolean requiresEdits) {
        String basePrompt = """
                You are a %s worker agent.
                Focus only on the assigned task. Be concise and practical.
                You must follow the expected output for this task.
                You may use MCP filesystem tools to read or list files in the workspace.
                When the task requires code changes, you MUST use MCP filesystem tools to read and write files to apply the changes.
                If the task does not explicitly instruct file edits, do not write files.
                If assumptions are required, list them explicitly.
                """.formatted(role);
        if (requiresEdits) {
            basePrompt = basePrompt + """

                    This request involves code changes. If your task's expected output says to apply changes, you must do so by editing files.
                    """;
        }
        List<AgentSkill> skills = properties.getSkills().getSkillsForWorkerRole(role);
        return appendSkillsToPrompt(basePrompt, skills);
    }

    private String synthesisSystemPrompt() {
        String basePrompt = """
                You are the synthesis agent. Combine worker outputs into a single, coherent response.
                Resolve conflicts, remove duplication, and answer the user's request directly.
                If MCP tool output was used, summarize relevant file changes accurately.
                """;
        return appendSkillsToPrompt(basePrompt, properties.getSkills().getSynthesis());
    }

    private String appendSkillsToPrompt(String basePrompt, List<AgentSkill> skills) {
        if (skills == null || skills.isEmpty()) {
            return basePrompt;
        }
        StringBuilder sb = new StringBuilder(basePrompt);
        sb.append("\n\nYou have the following skills:\n");
        for (AgentSkill skill : skills) {
            sb.append("\n### ").append(skill.getName());
            if (skill.getDescription() != null && !skill.getDescription().isBlank()) {
                sb.append("\n").append(skill.getDescription());
            }
            if (skill.getInstructions() != null && !skill.getInstructions().isBlank()) {
                sb.append("\nInstructions: ").append(skill.getInstructions());
            }
            sb.append("\n");
        }
        return sb.toString();
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

    private String preferredWriterRole(List<String> allowedRoles) {
        if (allowedRoles.contains("engineering")) {
            return "engineering";
        }
        if (allowedRoles.contains("general")) {
            return "general";
        }
        return allowedRoles.isEmpty() ? "general" : allowedRoles.getFirst();
    }

    private String appendFileEditInstruction(String expectedOutput, boolean isWriter) {
        String base = StringUtils.hasText(expectedOutput) ? expectedOutput.trim() : "Provide concise, actionable output.";
        if (isWriter) {
            return base + """

                    Apply the requested changes directly to repository files using MCP filesystem tools (read and write).
                    Do not just describe changes. Summarize files modified and any follow-up steps.
                    """;
        }
        return base + """

                Do not modify files. Provide analysis or suggestions only.
                """;
    }

    private boolean requiresFileEdits(String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return false;
        }
        String text = userMessage.toLowerCase(Locale.ROOT);
        if (text.contains("modify your own code")
                || text.contains("edit the code")
                || text.contains("change the code")
                || text.contains("apply the changes")
                || text.contains("make the following changes")
                || text.contains("implement this")) {
            return true;
        }
        List<String> editVerbs = List.of(
                "modify", "change", "update", "fix", "add", "implement", "remove",
                "delete", "refactor", "rename", "create", "build", "wire", "adjust", "edit", "patch"
        );
        List<String> artifacts = List.of(
                "code", "repo", "repository", "project", "app", "application", "api",
                "endpoint", "controller", "service", "ui", "frontend", "backend", "css",
                "html", "javascript", "js", "java", "spring", "config", "yaml", "yml",
                "file", "files", "tests", "database", "schema", "table"
        );
        boolean hasVerb = editVerbs.stream().anyMatch(text::contains);
        boolean hasArtifact = artifacts.stream().anyMatch(text::contains);
        if (!hasVerb || !hasArtifact) {
            return false;
        }
        String trimmed = text.trim();
        boolean startsWithVerb = editVerbs.stream().anyMatch(verb -> trimmed.startsWith(verb + " "));
        boolean hasDirective = text.contains("please ")
                || text.contains("can you")
                || text.contains("could you")
                || text.contains("i want")
                || text.contains("i need")
                || text.contains("i'd like")
                || text.contains("we need")
                || text.contains("we want");
        return startsWithVerb || hasDirective;
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
