package com.bko.orchestration;

import static com.bko.orchestration.OrchestrationConstants.*;
import com.bko.config.MultiAgentProperties;
import com.bko.orchestration.model.OrchestrationResult;
import com.bko.orchestration.model.OrchestratorPlan;
import com.bko.orchestration.model.RoleSelection;
import com.bko.orchestration.model.TaskSpec;
import com.bko.orchestration.model.WorkerResult;
import com.bko.orchestration.service.FileEditDetectionService;
import com.bko.orchestration.service.JsonProcessingService;
import com.bko.orchestration.service.OrchestrationContextService;
import com.bko.orchestration.service.OrchestrationPromptService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrchestratorService {

    private final ChatClient chatClient;
    private final MultiAgentProperties properties;
    private final ExecutorService workerExecutor;
    private final ToolCallbackProvider toolCallbackProvider;
    private final AtomicLong llmRequestCount = new AtomicLong();
    private final AtomicLong planResponseCount = new AtomicLong();
    private final AtomicLong taskReceivedCount = new AtomicLong();
    private final AtomicLong taskExecutedCount = new AtomicLong();

    // Injected Services
    private final FileEditDetectionService fileEditDetectionService;
    private final OrchestrationPromptService orchestrationPromptService;
    private final OrchestrationContextService orchestrationContextService;
    private final JsonProcessingService jsonProcessingService;

    @PreDestroy
    public void shutdown() {
        workerExecutor.shutdown();
    }

    public OrchestrationResult orchestrate(String userMessage) {
        boolean requiresEdits = fileEditDetectionService.requiresFileEdits(userMessage);
        List<String> initialRoles = selectRoles(userMessage, requiresEdits, null);
        List<TaskSpec> advisoryTasks = buildAdvisoryTasks(userMessage, initialRoles);
        if (!advisoryTasks.isEmpty()) {
            log.info("Advisory tasks scheduled: {}.", advisoryTasks.size());
        }
        List<WorkerResult> advisoryResults = runAdvisoryTasks(userMessage, advisoryTasks, requiresEdits);
        String advisoryContext = orchestrationContextService.buildAdvisoryContext(advisoryResults);

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
        boolean requiresEdits = fileEditDetectionService.requiresFileEdits(userMessage);
        List<String> selectedRoles = selectRoles(userMessage, requiresEdits, null);
        OrchestratorPlan rawPlan = requestPlan(userMessage, requiresEdits, selectedRoles, null);
        return sanitizePlan(rawPlan, userMessage, requiresEdits, selectedRoles, false, false);
    }

    private OrchestratorPlan requestPlan(String userMessage, boolean requiresEdits,
                                         List<String> allowedRoles, @Nullable String context) {
        try {
            String registry = orchestrationContextService.buildRoleRegistry(allowedRoles);
            String systemPrompt = orchestrationPromptService.orchestratorSystemPrompt(requiresEdits, allowedRoles, registry);
            String normalizedContext = orchestrationContextService.defaultContext(context);
            logLlmRequest(PURPOSE_PLAN, null);
            String response = applyTools(chatClient.prompt())
                    .system(systemPrompt)
                    .user(user -> user.text(ORCHESTRATOR_USER_TEMPLATE)
                            .param("input", userMessage)
                            .param("context", normalizedContext))
                    .call()
                    .content();
            OrchestratorPlan plan = jsonProcessingService.parseJsonResponse(PURPOSE_PLAN, response, OrchestratorPlan.class);
            if (plan == null) {
                String retryPrompt = systemPrompt + INVALID_JSON_RETRY_PROMPT;
                logLlmRequest(PURPOSE_PLAN_RETRY, null);
                String retryResponse = applyTools(chatClient.prompt())
                        .system(retryPrompt)
                        .user(user -> user.text(ORCHESTRATOR_USER_TEMPLATE)
                                .param("input", userMessage)
                                .param("context", normalizedContext))
                        .call()
                        .content();
                plan = jsonProcessingService.parseJsonResponse(PURPOSE_PLAN_RETRY, retryResponse, OrchestratorPlan.class);
            }
            logPlanResponse(PURPOSE_PLAN, plan);
            return plan;
        } catch (Exception ex) {
            return null;
        }
    }

    private OrchestratorPlan requestContinuationPlan(String userMessage, boolean requiresEdits,
                                                     List<String> allowedRoles, @Nullable String context,
                                                     OrchestratorPlan plan, List<WorkerResult> results) {
        try {
            String systemPrompt = orchestrationPromptService.executionReviewPrompt(requiresEdits, allowedRoles);
            String normalizedContext = orchestrationContextService.defaultContext(context);
            String planJson = jsonProcessingService.toJson(plan);
            String resultsJson = jsonProcessingService.toJson(results);
            logLlmRequest(PURPOSE_PLAN_REVIEW, null);
            String response = applyTools(chatClient.prompt())
                    .system(systemPrompt)
                    .user(user -> user.text(EXECUTION_REVIEW_USER_TEMPLATE)
                            .param("input", userMessage)
                            .param("context", normalizedContext)
                            .param("plan", planJson)
                            .param("results", resultsJson))
                    .call()
                    .content();
            OrchestratorPlan continuation = jsonProcessingService.parseJsonResponse(PURPOSE_PLAN_REVIEW, response, OrchestratorPlan.class);
            if (continuation == null) {
                String retryPrompt = systemPrompt + INVALID_JSON_RETRY_PROMPT;
                logLlmRequest(PURPOSE_PLAN_REVIEW_RETRY, null);
                String retryResponse = applyTools(chatClient.prompt())
                        .system(retryPrompt)
                        .user(user -> user.text(EXECUTION_REVIEW_USER_TEMPLATE)
                                .param("input", userMessage)
                                .param("context", normalizedContext)
                                .param("plan", planJson)
                                .param("results", resultsJson))
                        .call()
                        .content();
                continuation = jsonProcessingService.parseJsonResponse(PURPOSE_PLAN_REVIEW_RETRY, retryResponse, OrchestratorPlan.class);
            }
            logPlanResponse(PURPOSE_PLAN_REVIEW, continuation);
            return continuation;
        } catch (Exception ex) {
            return null;
        }
    }

    private List<String> selectRoles(String userMessage, boolean requiresEdits, @Nullable String context) {
        List<String> availableRoles = normalizedRoles();
        String registry = orchestrationContextService.buildRoleRegistry(availableRoles);
        try {
            logLlmRequest(PURPOSE_ROLE_SELECTION, null);
            String response = applyTools(chatClient.prompt())
                    .system(orchestrationPromptService.roleSelectionPrompt(requiresEdits, availableRoles))
                    .user(user -> user.text(ROLE_SELECTION_USER_TEMPLATE)
                            .param("input", userMessage)
                            .param("context", orchestrationContextService.defaultContext(context))
                            .param("roles", registry))
                    .call()
                    .content();
            RoleSelection selection = jsonProcessingService.parseJsonResponse(PURPOSE_ROLE_SELECTION, response, RoleSelection.class);
            if (selection == null) {
                String retryPrompt = orchestrationPromptService.roleSelectionPrompt(requiresEdits, availableRoles)
                        + INVALID_JSON_RETRY_PROMPT;
                logLlmRequest(PURPOSE_ROLE_SELECTION_RETRY, null);
                String retryResponse = applyTools(chatClient.prompt())
                        .system(retryPrompt)
                        .user(user -> user.text(ROLE_SELECTION_USER_TEMPLATE)
                                .param("input", userMessage)
                                .param("context", orchestrationContextService.defaultContext(context))
                                .param("roles", registry))
                        .call()
                        .content();
                selection = jsonProcessingService.parseJsonResponse(PURPOSE_ROLE_SELECTION_RETRY, retryResponse, RoleSelection.class);
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
        if (selectedRoles.contains(ROLE_ANALYSIS)) {
            tasks.add(new TaskSpec(TASK_ID_ANALYSIS, ROLE_ANALYSIS,
                    ANALYSIS_TASK_DESCRIPTION,
                    ANALYSIS_TASK_EXPECTED_OUTPUT));
        }
        if (selectedRoles.contains(ROLE_DESIGN)) {
            tasks.add(new TaskSpec(TASK_ID_DESIGN, ROLE_DESIGN,
                    DESIGN_TASK_DESCRIPTION,
                    DESIGN_TASK_EXPECTED_OUTPUT));
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
                .filter(task -> ROLE_ANALYSIS.equals(task.role()))
                .findFirst()
                .orElse(null);
        if (analysisTask != null) {
            results.add(runWorker(userMessage, analysisTask, requiresEdits, null));
        }
        TaskSpec designTask = advisoryTasks.stream()
                .filter(task -> ROLE_DESIGN.equals(task.role()))
                .findFirst()
                .orElse(null);
        if (designTask != null) {
            String analysisContext = orchestrationContextService.buildResultsContext(results);
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
                    .filter(task -> ROLE_ENGINEERING.equals(task.role()))
                    .toList();
            List<TaskSpec> otherTasks = tasks.stream()
                    .filter(task -> !ROLE_ENGINEERING.equals(task.role()))
                    .toList();
            List<WorkerResult> results = new ArrayList<>(tasks.size());
            String engineeringContext = orchestrationContextService.mergeContexts(advisoryContext,
                    orchestrationContextService.buildResultsContext(orchestrationContextService.filterResultsByRole(priorResults, Set.of(ROLE_ENGINEERING))));
            for (TaskSpec task : engineeringTasks) {
                String taskContext = engineeringContext;
                WorkerResult result = CompletableFuture
                        .supplyAsync(() -> runWorker(userMessage, task, true, taskContext), workerExecutor)
                        .orTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                        .exceptionally(ex -> new WorkerResult(task.id(), task.role(),
                                WORKER_FAILED_MESSAGE + ex.getMessage()))
                        .join();
                results.add(result);
                engineeringContext = orchestrationContextService.mergeContexts(engineeringContext, orchestrationContextService.buildResultsContext(List.of(result)));
            }
            if (otherTasks.isEmpty()) {
                return results;
            }
            String remainingContext = engineeringContext;
            List<CompletableFuture<WorkerResult>> futures = otherTasks.stream()
                    .map(task -> CompletableFuture.supplyAsync(() -> runWorker(userMessage, task, true, remainingContext), workerExecutor)
                            .orTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                            .exceptionally(ex -> new WorkerResult(task.id(), task.role(),
                                    "Worker failed: " + ex.getMessage())))
                    .toList();
            results.addAll(futures.stream().map(CompletableFuture::join).toList());
            return results;
        }

        String context = orchestrationContextService.mergeContexts(advisoryContext, orchestrationContextService.buildResultsContext(priorResults));
        List<CompletableFuture<WorkerResult>> futures = tasks.stream()
                .map(task -> CompletableFuture.supplyAsync(() -> runWorker(userMessage, task, false, context), workerExecutor)
                        .orTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                        .exceptionally(ex -> new WorkerResult(task.id(), task.role(),
                                "Worker failed: " + ex.getMessage())))
                .toList();
        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    private WorkerResult runWorker(String userMessage, TaskSpec task, boolean requiresEdits, @Nullable String context) {
        String systemPrompt = orchestrationPromptService.workerSystemPrompt(task.role(), requiresEdits);
        logLlmRequest(PURPOSE_WORKER_TASK, task.role());
        String output = applyTools(chatClient.prompt())
                .system(systemPrompt)
                .user(user -> user.text(WORKER_USER_TEMPLATE)
                        .param("input", userMessage)
                        .param("context", orchestrationContextService.defaultContext(context))
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
        String planJson = jsonProcessingService.toJson(plan);
        String resultsJson = jsonProcessingService.toJson(results);
        logLlmRequest(PURPOSE_SYTHESIS, null);
        return applyTools(chatClient.prompt())
                .system(orchestrationPromptService.synthesisSystemPrompt())
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
            String id = StringUtils.hasText(task.id()) ? task.id() : TASK_PREFIX + (index + 1);
            String description = StringUtils.hasText(task.description()) ? task.description() : userMessage;
            String expectedOutput = StringUtils.hasText(task.expectedOutput())
                    ? task.expectedOutput()
                    : DEFAULT_EXPECTED_OUTPUT;
            if (requiresEdits) {
                boolean canEdit = ROLE_ENGINEERING.equals(role);
                expectedOutput = fileEditDetectionService.appendFileEditInstruction(expectedOutput, canEdit);
            }
            sanitized.add(new TaskSpec(id, role, description, expectedOutput));
        });
        if (requiresEdits && sanitized.stream().noneMatch(task -> ROLE_ENGINEERING.equals(task.role()))) {
            sanitized.add(new TaskSpec(TASK_ID_IMPLEMENTATION, ROLE_ENGINEERING, userMessage,
                    fileEditDetectionService.appendFileEditInstruction(DEFAULT_IMPLEMENTATION_INSTRUCTION, true)));
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
                ? (normalizedRoles.contains(ROLE_ENGINEERING) ? ROLE_ENGINEERING : fallbackRole(normalizedRoles))
                : fallbackRole(normalizedRoles);
        String expectedOutput = DEFAULT_COMPLETE_RESPONSE_INSTRUCTION;
        if (requiresEdits) {
            expectedOutput = fileEditDetectionService.appendFileEditInstruction(expectedOutput, ROLE_ENGINEERING.equals(role));
        }
        TaskSpec fallback = new TaskSpec(TASK_ID_FALLBACK, role, userMessage, expectedOutput);
        return new OrchestratorPlan(userMessage, List.of(fallback));
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
        if (requiresEdits && !roles.contains(ROLE_ENGINEERING)) {
            if (available.contains(ROLE_ENGINEERING)) {
                roles.add(ROLE_ENGINEERING);
            } else {
                roles.add(fallbackRole(available));
            }
        }
        return new ArrayList<>(roles);
    }

    private String fallbackRole(List<String> allowedRoles) {
        if (allowedRoles.contains(ROLE_GENERAL)) {
            return ROLE_GENERAL;
        }
        return allowedRoles.isEmpty() ? ROLE_GENERAL : allowedRoles.getFirst();
    }

    private ChatClient.ChatClientRequestSpec applyTools(ChatClient.ChatClientRequestSpec prompt) {
        if (toolCallbackProvider == null) {
            return prompt;
        }
        return prompt.toolCallbacks(toolCallbackProvider);
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
}
