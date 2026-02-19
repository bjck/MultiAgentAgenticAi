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
import com.bko.orchestration.service.ToolAccessPolicy;
import com.bko.orchestration.service.FilteringToolCallbackProvider;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.bko.entity.OrchestrationSession;
import com.bko.entity.TaskLog;
import com.bko.orchestration.service.OrchestrationPersistenceService;

import java.util.HashMap;
import java.util.Map;

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
@Slf4j
public class OrchestratorService {

    private final ChatClient chatClient;
    private final ChatClient openAiChatClient;
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
    private final ToolAccessPolicy toolAccessPolicy;
    private final OrchestrationPersistenceService persistenceService;
    private final ThreadLocal<OrchestrationSession> currentSession = new ThreadLocal<>();

    public OrchestratorService(
            ChatClient chatClient,
            @Qualifier("openAiChatClient") ObjectProvider<ChatClient> openAiChatClientProvider,
            MultiAgentProperties properties,
            ExecutorService workerExecutor,
            ToolCallbackProvider toolCallbackProvider,
            FileEditDetectionService fileEditDetectionService,
            OrchestrationPromptService orchestrationPromptService,
            OrchestrationContextService orchestrationContextService,
            JsonProcessingService jsonProcessingService,
            ToolAccessPolicy toolAccessPolicy,
            OrchestrationPersistenceService persistenceService) {
        this.chatClient = chatClient;
        this.openAiChatClient = openAiChatClientProvider.getIfAvailable();
        this.properties = properties;
        this.workerExecutor = workerExecutor;
        this.toolCallbackProvider = toolCallbackProvider;
        this.fileEditDetectionService = fileEditDetectionService;
        this.orchestrationPromptService = orchestrationPromptService;
        this.orchestrationContextService = orchestrationContextService;
        this.jsonProcessingService = jsonProcessingService;
        this.toolAccessPolicy = toolAccessPolicy;
        this.persistenceService = persistenceService;
    }

    @PreDestroy
    public void shutdown() {
        workerExecutor.shutdown();
    }

    private ChatClient.ChatClientRequestSpec getChatRequestSpec(String provider, String model) {
        String activeProvider = StringUtils.hasText(provider) ? provider.toUpperCase() : properties.getAiProvider().name();
        String activeModel = StringUtils.hasText(model) ? model : properties.getOpenai().getModel();

        if ("OPENAI".equals(activeProvider)) {
            if (openAiChatClient == null) {
                throw new IllegalStateException("OpenAI provider is not properly configured. " +
                        "Check that you have a valid API key or a custom Base URL in your configuration.");
            }
            var spec = openAiChatClient.prompt();
            if (StringUtils.hasText(activeModel)) {
                spec = spec.options(org.springframework.ai.openai.OpenAiChatOptions.builder().model(activeModel).build());
            }
            return spec;
        }
        return chatClient.prompt();
    }

    public OrchestrationResult orchestrate(String userMessage, String provider, String model) {
        OrchestrationSession session = persistenceService.startSession(userMessage, provider, model);
        currentSession.set(session);
        try {
            boolean requiresEdits = fileEditDetectionService.requiresFileEdits(userMessage);
            List<String> initialRoles = selectRoles(userMessage, requiresEdits, null, provider, model);
            List<TaskSpec> advisoryTasks = buildAdvisoryTasks(userMessage, initialRoles);
            if (!advisoryTasks.isEmpty()) {
                log.info("Advisory tasks scheduled: {}.", advisoryTasks.size());
            }
            List<WorkerResult> advisoryResults = runAdvisoryTasks(session, userMessage, advisoryTasks, requiresEdits, provider, model);
            String advisoryContext = orchestrationContextService.buildAdvisoryContext(advisoryResults);

            List<String> executionRoles = selectRoles(userMessage, requiresEdits, advisoryContext, provider, model);
            OrchestratorPlan rawPlan = requestPlan(userMessage, requiresEdits, executionRoles, advisoryContext, provider, model);
            OrchestratorPlan initialPlan = sanitizePlan(rawPlan, userMessage, requiresEdits, executionRoles, true, false);

            var planLog = persistenceService.logPlan(session, initialPlan, true);
            Map<String, TaskLog> taskIndex = new HashMap<>();
            taskIndex.putAll(persistenceService.logTasks(planLog, initialPlan.tasks()));

            List<TaskSpec> allTasks = new ArrayList<>(advisoryTasks);
            List<WorkerResult> allResults = new ArrayList<>(advisoryResults);

            OrchestratorPlan currentPlan = initialPlan;
            int iteration = 0;
            while (iteration < MAX_EXECUTION_ITERATIONS && currentPlan != null && !currentPlan.tasks().isEmpty()) {
                log.info("Executing plan iteration {} with {} tasks.", iteration + 1, currentPlan.tasks().size());
                List<WorkerResult> iterationResults = executePlanTasks(session, userMessage, currentPlan.tasks(),
                        requiresEdits, advisoryContext, allResults, provider, model, taskIndex);
                allResults.addAll(iterationResults);
                allTasks.addAll(currentPlan.tasks());

                OrchestratorPlan continuationRaw = requestContinuationPlan(userMessage, requiresEdits, executionRoles,
                        advisoryContext, currentPlan, allResults, provider, model);
                OrchestratorPlan continuationPlan = sanitizePlan(continuationRaw, userMessage, requiresEdits,
                        executionRoles, true, true);
                if (continuationPlan.tasks().isEmpty()) {
                    break;
                }
                var contPlanLog = persistenceService.logPlan(session, continuationPlan, false);
                taskIndex.putAll(persistenceService.logTasks(contPlanLog, continuationPlan.tasks()));
                currentPlan = continuationPlan;
                iteration++;
            }

            String objective = (initialPlan != null && StringUtils.hasText(initialPlan.objective()))
                    ? initialPlan.objective()
                    : userMessage;
            OrchestratorPlan finalPlan = new OrchestratorPlan(objective, allTasks);
            String finalAnswer = synthesize(session, userMessage, finalPlan, allResults, provider, model);
            persistenceService.completeSession(session, finalAnswer, "COMPLETED");
            logSummary();
            return new OrchestrationResult(finalPlan, allResults, finalAnswer);
        } finally {
            currentSession.remove();
        }
    }

    public OrchestratorPlan plan(String userMessage, String provider, String model) {
        OrchestrationSession session = persistenceService.startSession(userMessage, provider, model);
        currentSession.set(session);
        try {
            boolean requiresEdits = fileEditDetectionService.requiresFileEdits(userMessage);
            List<String> selectedRoles = selectRoles(userMessage, requiresEdits, null, provider, model);
            OrchestratorPlan rawPlan = requestPlan(userMessage, requiresEdits, selectedRoles, null, provider, model);
            OrchestratorPlan sanitized = sanitizePlan(rawPlan, userMessage, requiresEdits, selectedRoles, false, false);
            var planLog = persistenceService.logPlan(session, sanitized, true);
            persistenceService.logTasks(planLog, sanitized.tasks());
            persistenceService.completeSession(session, null, "PLANNED");
            return sanitized;
        } finally {
            currentSession.remove();
        }
    }

    private OrchestratorPlan requestPlan(String userMessage, boolean requiresEdits,
                                         List<String> allowedRoles, @Nullable String context,
                                         String provider, String model) {
        try {
            String registry = orchestrationContextService.buildRoleRegistry(allowedRoles);
            String systemPrompt = orchestrationPromptService.orchestratorSystemPrompt(requiresEdits, allowedRoles, registry);
            String normalizedContext = orchestrationContextService.defaultContext(context);
            logLlmRequest(PURPOSE_PLAN, null);
            String response = applyTools(getChatRequestSpec(provider, model), ToolAccessPolicy.Phase.ORCHESTRATOR, null)
                    .system(systemPrompt)
                    .user(user -> user.text(ORCHESTRATOR_USER_TEMPLATE)
                            .param("input", userMessage)
                            .param("context", normalizedContext))
                    .call()
                    .content();
            try {
                persistenceService.logPrompt(currentSession.get(), PURPOSE_PLAN, null, systemPrompt, ORCHESTRATOR_USER_TEMPLATE,
                        Map.of("input", userMessage, "context", normalizedContext), response);
            } catch (Exception ignore) { }
            OrchestratorPlan plan = jsonProcessingService.parseJsonResponse(PURPOSE_PLAN, response, OrchestratorPlan.class);
            if (plan == null) {
                String retryPrompt = systemPrompt + INVALID_JSON_RETRY_PROMPT;
                logLlmRequest(PURPOSE_PLAN_RETRY, null);
                String retryResponse = applyTools(getChatRequestSpec(provider, model), ToolAccessPolicy.Phase.ORCHESTRATOR, null)
                        .system(retryPrompt)
                        .user(user -> user.text(ORCHESTRATOR_USER_TEMPLATE)
                                .param("input", userMessage)
                                .param("context", normalizedContext))
                        .call()
                        .content();
                try {
                    persistenceService.logPrompt(currentSession.get(), PURPOSE_PLAN_RETRY, null, retryPrompt, ORCHESTRATOR_USER_TEMPLATE,
                            Map.of("input", userMessage, "context", normalizedContext), retryResponse);
                } catch (Exception ignore) { }
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
                                                     OrchestratorPlan plan, List<WorkerResult> results,
                                                     String provider, String model) {
        try {
            String systemPrompt = orchestrationPromptService.executionReviewPrompt(requiresEdits, allowedRoles);
            String normalizedContext = orchestrationContextService.defaultContext(context);
            String planJson = jsonProcessingService.toJson(plan);
            String resultsJson = jsonProcessingService.toJson(results);
            logLlmRequest(PURPOSE_PLAN_REVIEW, null);
            String response = applyTools(getChatRequestSpec(provider, model), ToolAccessPolicy.Phase.ORCHESTRATOR, null)
                    .system(systemPrompt)
                    .user(user -> user.text(EXECUTION_REVIEW_USER_TEMPLATE)
                            .param("input", userMessage)
                            .param("context", normalizedContext)
                            .param("plan", planJson)
                            .param("results", resultsJson))
                    .call()
                    .content();
            try {
                persistenceService.logPrompt(currentSession.get(), PURPOSE_PLAN_REVIEW, null, systemPrompt, EXECUTION_REVIEW_USER_TEMPLATE,
                        Map.of("input", userMessage, "context", normalizedContext, "plan", planJson, "results", resultsJson), response);
            } catch (Exception ignore) { }
            OrchestratorPlan continuation = jsonProcessingService.parseJsonResponse(PURPOSE_PLAN_REVIEW, response, OrchestratorPlan.class);
            if (continuation == null) {
                String retryPrompt = systemPrompt + INVALID_JSON_RETRY_PROMPT;
                logLlmRequest(PURPOSE_PLAN_REVIEW_RETRY, null);
                String retryResponse = applyTools(getChatRequestSpec(provider, model), ToolAccessPolicy.Phase.ORCHESTRATOR, null)
                        .system(retryPrompt)
                        .user(user -> user.text(EXECUTION_REVIEW_USER_TEMPLATE)
                                .param("input", userMessage)
                                .param("context", normalizedContext)
                                .param("plan", planJson)
                                .param("results", resultsJson))
                        .call()
                        .content();
                try {
                    persistenceService.logPrompt(currentSession.get(), PURPOSE_PLAN_REVIEW_RETRY, null, retryPrompt, EXECUTION_REVIEW_USER_TEMPLATE,
                            Map.of("input", userMessage, "context", normalizedContext, "plan", planJson, "results", resultsJson), retryResponse);
                } catch (Exception ignore) { }
                continuation = jsonProcessingService.parseJsonResponse(PURPOSE_PLAN_REVIEW_RETRY, retryResponse, OrchestratorPlan.class);
            }
            logPlanResponse(PURPOSE_PLAN_REVIEW, continuation);
            return continuation;
        } catch (Exception ex) {
            return null;
        }
    }

    private List<String> selectRoles(String userMessage, boolean requiresEdits, @Nullable String context, String provider, String model) {
        List<String> availableRoles = normalizedRoles();
        String registry = orchestrationContextService.buildRoleRegistry(availableRoles);
        try {
            String systemPrompt = orchestrationPromptService.roleSelectionPrompt(requiresEdits, availableRoles);
            String normalizedContext = orchestrationContextService.defaultContext(context);
            logLlmRequest(PURPOSE_ROLE_SELECTION, null);
            String response = applyTools(getChatRequestSpec(provider, model), ToolAccessPolicy.Phase.ORCHESTRATOR, null)
                    .system(systemPrompt)
                    .user(user -> user.text(ROLE_SELECTION_USER_TEMPLATE)
                            .param("input", userMessage)
                            .param("context", normalizedContext)
                            .param("roles", registry))
                    .call()
                    .content();
            try {
                persistenceService.logPrompt(currentSession.get(), PURPOSE_ROLE_SELECTION, null, systemPrompt, ROLE_SELECTION_USER_TEMPLATE,
                        Map.of("input", userMessage, "context", normalizedContext, "roles", registry), response);
            } catch (Exception ignore) { }
            RoleSelection selection = jsonProcessingService.parseJsonResponse(PURPOSE_ROLE_SELECTION, response, RoleSelection.class);
            if (selection == null) {
                String retryPrompt = orchestrationPromptService.roleSelectionPrompt(requiresEdits, availableRoles)
                        + INVALID_JSON_RETRY_PROMPT;
                logLlmRequest(PURPOSE_ROLE_SELECTION_RETRY, null);
                String retryResponse = applyTools(getChatRequestSpec(provider, model), ToolAccessPolicy.Phase.ORCHESTRATOR, null)
                        .system(retryPrompt)
                        .user(user -> user.text(ROLE_SELECTION_USER_TEMPLATE)
                                .param("input", userMessage)
                                .param("context", normalizedContext)
                                .param("roles", registry))
                        .call()
                        .content();
                try {
                    persistenceService.logPrompt(currentSession.get(), PURPOSE_ROLE_SELECTION_RETRY, null, retryPrompt, ROLE_SELECTION_USER_TEMPLATE,
                            Map.of("input", userMessage, "context", normalizedContext, "roles", registry), retryResponse);
                } catch (Exception ignore) { }
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

    private List<WorkerResult> runAdvisoryTasks(OrchestrationSession session, String userMessage, List<TaskSpec> advisoryTasks, boolean requiresEdits, String provider, String model) {
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
            results.add(runWorker(session, userMessage, analysisTask, requiresEdits, null, provider, model, null));
        }
        TaskSpec designTask = advisoryTasks.stream()
                .filter(task -> ROLE_DESIGN.equals(task.role()))
                .findFirst()
                .orElse(null);
        if (designTask != null) {
            String analysisContext = orchestrationContextService.buildResultsContext(results);
            results.add(runWorker(session, userMessage, designTask, requiresEdits, analysisContext, provider, model, null));
        }
        return results;
    }

    private List<WorkerResult> executePlanTasks(OrchestrationSession session, String userMessage, List<TaskSpec> tasks, boolean requiresEdits,
                                                String advisoryContext, List<WorkerResult> priorResults, String provider, String model, Map<String, TaskLog> taskIndex) {
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
                TaskLog taskLog = taskIndex.get(task.id());
                WorkerResult result = CompletableFuture
                        .supplyAsync(() -> runWorker(session, userMessage, task, true, taskContext, provider, model, taskLog), workerExecutor)
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
                    .map(task -> {
                        TaskLog tl = taskIndex.get(task.id());
                        return CompletableFuture.supplyAsync(() -> runWorker(session, userMessage, task, true, remainingContext, provider, model, tl), workerExecutor)
                                .orTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                                .exceptionally(ex -> new WorkerResult(task.id(), task.role(),
                                        "Worker failed: " + ex.getMessage()));
                    })
                    .toList();
            results.addAll(futures.stream().map(CompletableFuture::join).toList());
            return results;
        }

        String context = orchestrationContextService.mergeContexts(advisoryContext, orchestrationContextService.buildResultsContext(priorResults));
        List<CompletableFuture<WorkerResult>> futures = tasks.stream()
                .map(task -> {
                    TaskLog tl = taskIndex.get(task.id());
                    return CompletableFuture.supplyAsync(() -> runWorker(session, userMessage, task, false, context, provider, model, tl), workerExecutor)
                            .orTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                            .exceptionally(ex -> new WorkerResult(task.id(), task.role(),
                                    "Worker failed: " + ex.getMessage()));
                })
                .toList();
        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    private WorkerResult runWorker(OrchestrationSession session, String userMessage, TaskSpec task, boolean requiresEdits, @Nullable String context, String provider, String model, @Nullable TaskLog taskLog) {
        String systemPrompt = orchestrationPromptService.workerSystemPrompt(task.role(), requiresEdits);
        logLlmRequest(PURPOSE_WORKER_TASK, task.role());
        String normalizedContext = orchestrationContextService.defaultContext(context);
        String output = applyTools(getChatRequestSpec(provider, model), ToolAccessPolicy.Phase.WORKER, task.role())
                .system(systemPrompt)
                .user(user -> user.text(WORKER_USER_TEMPLATE)
                        .param("input", userMessage)
                        .param("context", normalizedContext)
                        .param("task", task.description())
                        .param("expectedOutput", task.expectedOutput()))
                .call()
                .content();
        try {
            Map<String, String> params = Map.of(
                    "input", userMessage,
                    "context", normalizedContext,
                    "task", task.description(),
                    "expectedOutput", task.expectedOutput()
            );
            persistenceService.logPrompt(session, PURPOSE_WORKER_TASK, task.role(), systemPrompt, WORKER_USER_TEMPLATE, params, output);
            persistenceService.logWorkerResult(session, taskLog, task.role(), output);
        } catch (Exception ignore) { }
        return new WorkerResult(task.id(), task.role(), output);
    }

    private String synthesize(OrchestrationSession session, String userMessage, OrchestratorPlan plan, List<WorkerResult> results, String provider, String model) {
        if (results.size() == 1 && !ADVISORY_ROLES.contains(results.getFirst().role())) {
            return results.getFirst().output();
        }
        String planJson = jsonProcessingService.toJson(plan);
        String resultsJson = jsonProcessingService.toJson(results);
        logLlmRequest(PURPOSE_SYTHESIS, null);
        String systemPrompt = orchestrationPromptService.synthesisSystemPrompt();
        String out = applyTools(getChatRequestSpec(provider, model), ToolAccessPolicy.Phase.SYNTHESIS, null)
                .system(systemPrompt)
                .user(user -> user.text(SYNTHESIS_USER_TEMPLATE)
                        .param("input", userMessage)
                        .param("plan", planJson)
                        .param("results", resultsJson))
                .call()
                .content();
        try {
            persistenceService.logPrompt(session, PURPOSE_SYTHESIS, null, systemPrompt, SYNTHESIS_USER_TEMPLATE,
                    Map.of("input", userMessage, "plan", planJson, "results", resultsJson), out);
        } catch (Exception ignore) { }
        return out;
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

    private ChatClient.ChatClientRequestSpec applyTools(ChatClient.ChatClientRequestSpec prompt, ToolAccessPolicy.Phase phase, @Nullable String role) {
        if (toolCallbackProvider == null) {
            return prompt;
        }
        var allowed = toolAccessPolicy.allowedToolNames(phase, role);
        var filtered = new FilteringToolCallbackProvider(toolCallbackProvider, allowed);
        return prompt.toolCallbacks(filtered);
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
