package com.bko.orchestration;

import com.bko.config.AgentSkill;
import com.bko.config.MultiAgentProperties;
import com.bko.orchestration.model.OrchestrationResult;
import com.bko.orchestration.model.OrchestratorPlan;
import com.bko.orchestration.model.RoleSelection;
import com.bko.orchestration.model.TaskSpec;
import com.bko.orchestration.model.WorkerResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

@Service
public class OrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorService.class);

    private static final String ANALYSIS_ROLE = "analysis";
    private static final String DESIGN_ROLE = "design";
    private static final String ENGINEERING_ROLE = "engineering";
    private static final Set<String> ADVISORY_ROLES = Set.of(ANALYSIS_ROLE, DESIGN_ROLE);
    private static final int MAX_EXECUTION_ITERATIONS = 3;

    private static final String ORCHESTRATOR_USER_TEMPLATE = """
            User request:
            {input}

            Context:
            {context}
            """;

    private static final String ROLE_SELECTION_USER_TEMPLATE = """
            User request:
            {input}

            Context:
            {context}

            Available roles and skills:
            {roles}
            """;

    private static final String EXECUTION_REVIEW_USER_TEMPLATE = """
            User request:
            {input}

            Context:
            {context}

            Current plan:
            {plan}

            Worker outputs so far:
            {results}
            """;

    private static final String WORKER_USER_TEMPLATE = """
            User request:
            {input}

            Context:
            {context}

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
    private final AtomicLong llmRequestCount = new AtomicLong();
    private final AtomicLong planResponseCount = new AtomicLong();
    private final AtomicLong taskReceivedCount = new AtomicLong();
    private final AtomicLong taskExecutedCount = new AtomicLong();

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
        List<String> initialRoles = selectRoles(userMessage, requiresEdits, null);
        List<TaskSpec> advisoryTasks = buildAdvisoryTasks(userMessage, initialRoles);
        if (!advisoryTasks.isEmpty()) {
            log.info("Advisory tasks scheduled: {}.", advisoryTasks.size());
        }
        List<WorkerResult> advisoryResults = runAdvisoryTasks(userMessage, advisoryTasks, requiresEdits);
        String advisoryContext = buildAdvisoryContext(advisoryResults);

        List<String> executionRoles = selectRoles(userMessage, requiresEdits, advisoryContext);
        OrchestratorPlan rawPlan = requestPlan(userMessage, requiresEdits, executionRoles, advisoryContext);
        OrchestratorPlan initialPlan = sanitizePlan(rawPlan, userMessage, requiresEdits, executionRoles, true, false);

        List<TaskSpec> allTasks = new ArrayList<>(advisoryTasks);
        List<WorkerResult> allResults = new ArrayList<>(advisoryResults);

        OrchestratorPlan currentPlan = initialPlan;
        int iteration = 0;
        while (iteration < MAX_EXECUTION_ITERATIONS && currentPlan != null && !currentPlan.tasks().isEmpty()) {
            log.info("Executing plan iteration {} with {} tasks.", iteration + 1, currentPlan.tasks().size());
            List<WorkerResult> iterationResults = executePlanTasks(userMessage, currentPlan.tasks(),
                    requiresEdits, advisoryContext, allResults);
            allResults.addAll(iterationResults);
            allTasks.addAll(currentPlan.tasks());

            OrchestratorPlan continuationRaw = requestContinuationPlan(userMessage, requiresEdits, executionRoles,
                    advisoryContext, currentPlan, allResults);
            OrchestratorPlan continuationPlan = sanitizePlan(continuationRaw, userMessage, requiresEdits,
                    executionRoles, true, true);
            if (continuationPlan.tasks().isEmpty()) {
                break;
            }
            currentPlan = continuationPlan;
            iteration++;
        }

        String objective = (initialPlan != null && StringUtils.hasText(initialPlan.objective()))
                ? initialPlan.objective()
                : userMessage;
        OrchestratorPlan finalPlan = new OrchestratorPlan(objective, allTasks);
        String finalAnswer = synthesize(userMessage, finalPlan, allResults);
        logSummary();
        return new OrchestrationResult(finalPlan, allResults, finalAnswer);
    }

    public OrchestratorPlan plan(String userMessage) {
        boolean requiresEdits = requiresFileEdits(userMessage);
        List<String> selectedRoles = selectRoles(userMessage, requiresEdits, null);
        OrchestratorPlan rawPlan = requestPlan(userMessage, requiresEdits, selectedRoles, null);
        return sanitizePlan(rawPlan, userMessage, requiresEdits, selectedRoles, false, false);
    }

    private OrchestratorPlan requestPlan(String userMessage, boolean requiresEdits,
                                         List<String> allowedRoles, @Nullable String context) {
        try {
            String systemPrompt = orchestratorSystemPrompt(requiresEdits, allowedRoles);
            String normalizedContext = defaultContext(context);
            logLlmRequest("plan", null);
            String response = applyTools(chatClient.prompt())
                    .system(systemPrompt)
                    .user(user -> user.text(ORCHESTRATOR_USER_TEMPLATE)
                            .param("input", userMessage)
                            .param("context", normalizedContext))
                    .call()
                    .content();
            OrchestratorPlan plan = parseJsonResponse("plan", response, OrchestratorPlan.class);
            if (plan == null) {
                String retryPrompt = systemPrompt + "\nYour last response was invalid JSON. Return only valid JSON.";
                logLlmRequest("plan-retry", null);
                String retryResponse = applyTools(chatClient.prompt())
                        .system(retryPrompt)
                        .user(user -> user.text(ORCHESTRATOR_USER_TEMPLATE)
                                .param("input", userMessage)
                                .param("context", normalizedContext))
                        .call()
                        .content();
                plan = parseJsonResponse("plan-retry", retryResponse, OrchestratorPlan.class);
            }
            logPlanResponse("plan", plan);
            return plan;
        } catch (Exception ex) {
            return null;
        }
    }

    private OrchestratorPlan requestContinuationPlan(String userMessage, boolean requiresEdits,
                                                     List<String> allowedRoles, @Nullable String context,
                                                     OrchestratorPlan plan, List<WorkerResult> results) {
        try {
            String systemPrompt = executionReviewPrompt(requiresEdits, allowedRoles);
            String normalizedContext = defaultContext(context);
            String planJson = toJson(plan);
            String resultsJson = toJson(results);
            logLlmRequest("plan-review", null);
            String response = applyTools(chatClient.prompt())
                    .system(systemPrompt)
                    .user(user -> user.text(EXECUTION_REVIEW_USER_TEMPLATE)
                            .param("input", userMessage)
                            .param("context", normalizedContext)
                            .param("plan", planJson)
                            .param("results", resultsJson))
                    .call()
                    .content();
            OrchestratorPlan continuation = parseJsonResponse("plan-review", response, OrchestratorPlan.class);
            if (continuation == null) {
                String retryPrompt = systemPrompt + "\nYour last response was invalid JSON. Return only valid JSON.";
                logLlmRequest("plan-review-retry", null);
                String retryResponse = applyTools(chatClient.prompt())
                        .system(retryPrompt)
                        .user(user -> user.text(EXECUTION_REVIEW_USER_TEMPLATE)
                                .param("input", userMessage)
                                .param("context", normalizedContext)
                                .param("plan", planJson)
                                .param("results", resultsJson))
                        .call()
                        .content();
                continuation = parseJsonResponse("plan-review-retry", retryResponse, OrchestratorPlan.class);
            }
            logPlanResponse("plan-review", continuation);
            return continuation;
        } catch (Exception ex) {
            return null;
        }
    }

    private List<String> selectRoles(String userMessage, boolean requiresEdits, @Nullable String context) {
        List<String> availableRoles = normalizedRoles();
        String registry = buildRoleRegistry(availableRoles);
        try {
            logLlmRequest("role-selection", null);
            String response = applyTools(chatClient.prompt())
                    .system(roleSelectionPrompt(requiresEdits, availableRoles))
                    .user(user -> user.text(ROLE_SELECTION_USER_TEMPLATE)
                            .param("input", userMessage)
                            .param("context", defaultContext(context))
                            .param("roles", registry))
                    .call()
                    .content();
            RoleSelection selection = parseJsonResponse("role-selection", response, RoleSelection.class);
            if (selection == null) {
                String retryPrompt = roleSelectionPrompt(requiresEdits, availableRoles)
                        + "\nYour last response was invalid JSON. Return only valid JSON.";
                logLlmRequest("role-selection-retry", null);
                String retryResponse = applyTools(chatClient.prompt())
                        .system(retryPrompt)
                        .user(user -> user.text(ROLE_SELECTION_USER_TEMPLATE)
                                .param("input", userMessage)
                                .param("context", defaultContext(context))
                                .param("roles", registry))
                        .call()
                        .content();
                selection = parseJsonResponse("role-selection-retry", retryResponse, RoleSelection.class);
            }
            List<String> roles = sanitizeSelectedRoles(selection != null ? selection.roles() : null, requiresEdits);
            log.info("Selected roles: {}.", String.join(", ", roles));
            return roles;
        } catch (Exception ex) {
            List<String> roles = sanitizeSelectedRoles(null, requiresEdits);
            log.info("Selected roles (fallback): {}.", String.join(", ", roles));
            return roles;
        }
    }

    private List<TaskSpec> buildAdvisoryTasks(String userMessage, List<String> selectedRoles) {
        List<TaskSpec> tasks = new ArrayList<>();
        if (selectedRoles.contains(ANALYSIS_ROLE)) {
            tasks.add(new TaskSpec("analysis-1", ANALYSIS_ROLE,
                    "Analyze the user request. Identify requirements, constraints, risks, and edge cases.",
                    "Provide structured analysis and open questions if any. Do not modify files."));
        }
        if (selectedRoles.contains(DESIGN_ROLE)) {
            tasks.add(new TaskSpec("design-1", DESIGN_ROLE,
                    "Propose a design/approach for the request, including components, APIs, data flow, and steps.",
                    "Provide a clear design and implementation guidance. Do not modify files."));
        }
        return tasks;
    }

    private List<WorkerResult> runAdvisoryTasks(String userMessage, List<TaskSpec> advisoryTasks, boolean requiresEdits) {
        if (advisoryTasks == null || advisoryTasks.isEmpty()) {
            return List.of();
        }
        long totalExecuted = taskExecutedCount.addAndGet(advisoryTasks.size());
        log.info("Executing {} advisory tasks. Total tasks executed so far={}.", advisoryTasks.size(), totalExecuted);
        List<WorkerResult> results = new ArrayList<>();
        TaskSpec analysisTask = advisoryTasks.stream()
                .filter(task -> ANALYSIS_ROLE.equals(task.role()))
                .findFirst()
                .orElse(null);
        if (analysisTask != null) {
            results.add(runWorker(userMessage, analysisTask, requiresEdits, null));
        }
        TaskSpec designTask = advisoryTasks.stream()
                .filter(task -> DESIGN_ROLE.equals(task.role()))
                .findFirst()
                .orElse(null);
        if (designTask != null) {
            String analysisContext = buildResultsContext(results);
            results.add(runWorker(userMessage, designTask, requiresEdits, analysisContext));
        }
        return results;
    }

    private List<WorkerResult> executePlanTasks(String userMessage, List<TaskSpec> tasks, boolean requiresEdits,
                                                String advisoryContext, List<WorkerResult> priorResults) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        long totalExecuted = taskExecutedCount.addAndGet(tasks.size());
        log.info("Executing {} plan tasks. Total tasks executed so far={}.", tasks.size(), totalExecuted);
        Duration timeout = properties.getWorkerTimeout();
        if (requiresEdits) {
            List<TaskSpec> engineeringTasks = tasks.stream()
                    .filter(task -> ENGINEERING_ROLE.equals(task.role()))
                    .toList();
            List<TaskSpec> otherTasks = tasks.stream()
                    .filter(task -> !ENGINEERING_ROLE.equals(task.role()))
                    .toList();
            List<WorkerResult> results = new ArrayList<>(tasks.size());
            String engineeringContext = mergeContexts(advisoryContext,
                    buildResultsContext(filterResultsByRole(priorResults, Set.of(ENGINEERING_ROLE))));
            for (TaskSpec task : engineeringTasks) {
                String taskContext = engineeringContext;
                WorkerResult result = CompletableFuture
                        .supplyAsync(() -> runWorker(userMessage, task, true, taskContext), executor)
                        .orTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                        .exceptionally(ex -> new WorkerResult(task.id(), task.role(),
                                "Worker failed: " + ex.getMessage()))
                        .join();
                results.add(result);
                engineeringContext = mergeContexts(engineeringContext, buildResultsContext(List.of(result)));
            }
            if (otherTasks.isEmpty()) {
                return results;
            }
            String remainingContext = engineeringContext;
            List<CompletableFuture<WorkerResult>> futures = otherTasks.stream()
                    .map(task -> CompletableFuture.supplyAsync(() -> runWorker(userMessage, task, true, remainingContext), executor)
                            .orTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                            .exceptionally(ex -> new WorkerResult(task.id(), task.role(),
                                    "Worker failed: " + ex.getMessage())))
                    .toList();
            results.addAll(futures.stream().map(CompletableFuture::join).toList());
            return results;
        }

        String context = mergeContexts(advisoryContext, buildResultsContext(priorResults));
        List<CompletableFuture<WorkerResult>> futures = tasks.stream()
                .map(task -> CompletableFuture.supplyAsync(() -> runWorker(userMessage, task, false, context), executor)
                        .orTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                        .exceptionally(ex -> new WorkerResult(task.id(), task.role(),
                                "Worker failed: " + ex.getMessage())))
                .toList();
        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    private WorkerResult runWorker(String userMessage, TaskSpec task, boolean requiresEdits, @Nullable String context) {
        String systemPrompt = workerSystemPrompt(task.role(), requiresEdits);
        logLlmRequest("worker-task", task.role());
        String output = applyTools(chatClient.prompt())
                .system(systemPrompt)
                .user(user -> user.text(WORKER_USER_TEMPLATE)
                        .param("input", userMessage)
                        .param("context", defaultContext(context))
                        .param("task", task.description())
                        .param("expectedOutput", task.expectedOutput()))
                .call()
                .content();
        return new WorkerResult(task.id(), task.role(), output);
    }

    private String synthesize(String userMessage, OrchestratorPlan plan, List<WorkerResult> results) {
        if (results.size() == 1 && !ADVISORY_ROLES.contains(results.getFirst().role())) {
            return results.getFirst().output();
        }
        String planJson = toJson(plan);
        String resultsJson = toJson(results);
        logLlmRequest("synthesis", null);
        return applyTools(chatClient.prompt())
                .system(synthesisSystemPrompt())
                .user(user -> user.text(SYNTHESIS_USER_TEMPLATE)
                        .param("input", userMessage)
                        .param("plan", planJson)
                        .param("results", resultsJson))
                .call()
                .content();
    }

    private OrchestratorPlan sanitizePlan(OrchestratorPlan plan, String userMessage, boolean requiresEdits,
                                          List<String> allowedRoles, boolean excludeAdvisory, boolean allowEmpty) {
        if (plan == null || plan.tasks() == null) {
            return allowEmpty ? new OrchestratorPlan(userMessage, List.of())
                    : defaultPlan(userMessage, requiresEdits, allowedRoles);
        }
        String objective = StringUtils.hasText(plan.objective()) ? plan.objective() : userMessage;
        List<TaskSpec> incomingTasks = plan.tasks();
        int maxTasks = Math.min(properties.getMaxTasks(), incomingTasks.size());
        List<TaskSpec> sanitized = new ArrayList<>(maxTasks);
        List<String> normalizedRoles = normalizeAllowedRoles(allowedRoles);
        IntStream.range(0, maxTasks).forEach(index -> {
            TaskSpec task = incomingTasks.get(index);
            String role = normalizeRole(task.role(), normalizedRoles);
            if (excludeAdvisory && ADVISORY_ROLES.contains(role)) {
                return;
            }
            String id = StringUtils.hasText(task.id()) ? task.id() : "task-" + (index + 1);
            String description = StringUtils.hasText(task.description()) ? task.description() : userMessage;
            String expectedOutput = StringUtils.hasText(task.expectedOutput())
                    ? task.expectedOutput()
                    : "Provide concise, actionable output.";
            if (requiresEdits) {
                boolean canEdit = ENGINEERING_ROLE.equals(role);
                expectedOutput = appendFileEditInstruction(expectedOutput, canEdit);
            }
            sanitized.add(new TaskSpec(id, role, description, expectedOutput));
        });
        if (requiresEdits && sanitized.stream().noneMatch(task -> ENGINEERING_ROLE.equals(task.role()))) {
            sanitized.add(new TaskSpec("task-impl", ENGINEERING_ROLE, userMessage,
                    appendFileEditInstruction("Implement the requested changes.", true)));
        }
        if (sanitized.isEmpty()) {
            return allowEmpty ? new OrchestratorPlan(objective, List.of())
                    : defaultPlan(userMessage, requiresEdits, allowedRoles);
        }
        return new OrchestratorPlan(objective, sanitized);
    }

    private OrchestratorPlan defaultPlan(String userMessage, boolean requiresEdits, List<String> allowedRoles) {
        List<String> normalizedRoles = normalizeAllowedRoles(allowedRoles);
        String role = requiresEdits
                ? (normalizedRoles.contains(ENGINEERING_ROLE) ? ENGINEERING_ROLE : fallbackRole(normalizedRoles))
                : fallbackRole(normalizedRoles);
        String expectedOutput = "Provide a complete response to the user request.";
        if (requiresEdits) {
            expectedOutput = appendFileEditInstruction(expectedOutput, ENGINEERING_ROLE.equals(role));
        }
        TaskSpec fallback = new TaskSpec("task-1", role, userMessage, expectedOutput);
        return new OrchestratorPlan(userMessage, List.of(fallback));
    }

    private String roleSelectionPrompt(boolean requiresEdits, List<String> allowedRoles) {
        String basePrompt = """
                You are the agent selector. Choose the minimal set of worker roles needed to satisfy the user request.
                Only choose from: %s.
                Use the role skill registry to match roles to required skills.
                Order roles in likely execution order (analysis/design first, engineering later).
                Return only JSON that matches: {\"roles\": [\"role1\", ...]}.
                """.formatted(String.join(", ", allowedRoles));
        if (requiresEdits) {
            basePrompt += "\nAlways include the engineering role because code changes are required.\n";
        }
        return basePrompt;
    }

    private String orchestratorSystemPrompt(boolean requiresEdits, List<String> allowedRoles) {
        String registry = buildRoleRegistry(allowedRoles);
        String basePrompt = """
                You are the Orchestrator agent. Break the user's request into up to %d parallel tasks.
                Only assign roles from: %s.
                Match tasks to the role skill registry below.
                Analysis and design tasks are advisory and must not modify files.
                Engineering tasks should apply file edits when required.
                Keep tasks independent and specific. Each task should be actionable by a single worker.
                You may use MCP filesystem tools to inspect the workspace when needed.
                If the user requests code or content changes, ensure at least one task is explicitly responsible for applying file edits via MCP filesystem tools.
                Make it clear which task should write files and which should not.
                Return only JSON that matches the requested schema.

                Role skill registry:
                %s
                """.formatted(properties.getMaxTasks(), String.join(", ", allowedRoles), registry);
        if (requiresEdits) {
            basePrompt = basePrompt + """

                    The user request requires code changes. Ensure the plan includes implementation tasks that will modify files.
                    """;
        }
        return appendSkillsToPrompt(basePrompt, properties.getSkills().getOrchestrator());
    }

    private String executionReviewPrompt(boolean requiresEdits, List<String> allowedRoles) {
        String basePrompt = """
                You are the execution reviewer. Decide if additional tasks are required to fully satisfy the user request.
                Only assign roles from: %s.
                Do NOT include analysis or design tasks; those are already complete.
                If the work is complete, return an empty tasks array.
                Return only JSON that matches the requested schema.
                """.formatted(String.join(", ", allowedRoles));
        if (requiresEdits) {
            basePrompt += "\nIf edits are still required, ensure engineering tasks perform them.\n";
        }
        return basePrompt;
    }

    private String workerSystemPrompt(String role, boolean requiresEdits) {
        String basePrompt = """
                You are a %s worker agent.
                Focus only on the assigned task. Be concise and practical.
                You must follow the expected output for this task.
                You may use MCP filesystem tools to read or list files in the workspace.
                When the task requires code changes, you MUST use MCP filesystem tools to read and write files to apply the changes.
                If the task does not explicitly instruct file edits, do not write files.
                Only the engineering role should apply file edits.
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
            return fallbackRole(allowedRoles);
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        return allowedRoles.contains(normalized) ? normalized : fallbackRole(allowedRoles);
    }

    private List<String> normalizedRoles() {
        return properties.getWorkerRoles().stream()
                .filter(StringUtils::hasText)
                .map(role -> role.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private List<String> normalizeAllowedRoles(List<String> allowedRoles) {
        List<String> normalized = allowedRoles == null ? List.of() : allowedRoles.stream()
                .filter(StringUtils::hasText)
                .map(role -> role.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            return normalizedRoles();
        }
        return normalized;
    }

    private List<String> sanitizeSelectedRoles(List<String> selected, boolean requiresEdits) {
        List<String> available = normalizedRoles();
        LinkedHashSet<String> roles = new LinkedHashSet<>();
        if (selected != null) {
            for (String role : selected) {
                if (!StringUtils.hasText(role)) {
                    continue;
                }
                String normalized = role.trim().toLowerCase(Locale.ROOT);
                if (available.contains(normalized)) {
                    roles.add(normalized);
                }
            }
        }
        if (roles.isEmpty()) {
            roles.addAll(available);
        }
        if (requiresEdits && !roles.contains(ENGINEERING_ROLE)) {
            if (available.contains(ENGINEERING_ROLE)) {
                roles.add(ENGINEERING_ROLE);
            } else {
                roles.add(fallbackRole(available));
            }
        }
        return new ArrayList<>(roles);
    }

    private String fallbackRole(List<String> allowedRoles) {
        if (allowedRoles.contains("general")) {
            return "general";
        }
        return allowedRoles.isEmpty() ? "general" : allowedRoles.getFirst();
    }

    private String appendFileEditInstruction(String expectedOutput, boolean canEdit) {
        String base = StringUtils.hasText(expectedOutput) ? expectedOutput.trim() : "Provide concise, actionable output.";
        if (canEdit) {
            return base + """

                    Apply the requested changes directly to repository files using MCP filesystem tools (read and write).
                    Do not just describe changes. Summarize files modified and any follow-up steps.
                    """;
        }
        return base + """

                Do not modify files. Provide analysis or suggestions only.
                """;
    }

    private String buildRoleRegistry(List<String> roles) {
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

    private String buildAdvisoryContext(List<WorkerResult> results) {
        return buildResultsContext(filterResultsByRole(results, ADVISORY_ROLES));
    }

    private List<WorkerResult> filterResultsByRole(List<WorkerResult> results, Set<String> roles) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        return results.stream()
                .filter(result -> roles.contains(result.role()))
                .toList();
    }

    private String buildResultsContext(List<WorkerResult> results) {
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

    private String mergeContexts(String base, String addition) {
        if (!StringUtils.hasText(base)) {
            return StringUtils.hasText(addition) ? addition : "";
        }
        if (!StringUtils.hasText(addition)) {
            return base;
        }
        return base + "\n\n" + addition;
    }

    private String defaultContext(@Nullable String context) {
        return StringUtils.hasText(context) ? context : "None.";
    }

    private <T> @Nullable T parseJsonResponse(String label, @Nullable String raw, Class<T> type) {
        if (!StringUtils.hasText(raw)) {
            log.warn("Empty response for {}. Unable to parse JSON.", label);
            return null;
        }
        String json = extractJsonObject(raw);
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            log.warn("Failed to parse {} response as JSON. Snippet: {}", label, truncate(raw, 240));
            return null;
        }
    }

    private String extractJsonObject(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        }
        return trimmed;
    }

    private String truncate(String value, int maxLength) {
        String normalized = value.replace("\r", " ").replace("\n", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private void logLlmRequest(String purpose, @Nullable String role) {
        long count = llmRequestCount.incrementAndGet();
        if (StringUtils.hasText(role)) {
            log.info("LLM request #{} sent (purpose={}, role={}). Total requests={}.", count, purpose, role, count);
        } else {
            log.info("LLM request #{} sent (purpose={}). Total requests={}.", count, purpose, count);
        }
    }

    private void logPlanResponse(String label, @Nullable OrchestratorPlan plan) {
        long planCount = planResponseCount.incrementAndGet();
        if (plan == null || plan.tasks() == null) {
            log.info("Plan response #{} ({}) returned no tasks. Total plans={}.", planCount, label, planCount);
            return;
        }
        int taskCount = plan.tasks().size();
        long totalTasks = taskReceivedCount.addAndGet(taskCount);
        log.info("Plan response #{} ({}) received {} tasks. Total plans={}, total tasks received={}.",
                planCount, label, taskCount, planCount, totalTasks);
    }

    private void logSummary() {
        log.info("LLM stats: totalRequests={}, totalPlans={}, totalTasksReceived={}, totalTasksExecuted={}.",
                llmRequestCount.get(), planResponseCount.get(), taskReceivedCount.get(), taskExecutedCount.get());
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
