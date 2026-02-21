package com.bko.orchestration;

import static com.bko.orchestration.OrchestrationConstants.*;
import com.bko.config.MultiAgentProperties;
import com.bko.orchestration.collaboration.CollaborationStage;
import com.bko.orchestration.collaboration.CollaborationStrategy;
import com.bko.orchestration.collaboration.CollaborationStrategyService;
import com.bko.orchestration.model.OrchestrationResult;
import com.bko.orchestration.model.OrchestratorPlan;
import com.bko.orchestration.model.PlanDraft;
import com.bko.orchestration.model.RoleSelection;
import com.bko.orchestration.model.TaskSpec;
import com.bko.orchestration.model.WorkerResult;
import com.bko.orchestration.service.FileEditDetectionService;
import com.bko.orchestration.service.JsonProcessingService;
import com.bko.orchestration.service.OrchestrationContextService;
import com.bko.orchestration.service.OrchestrationPromptService;
import com.bko.orchestration.service.ToolAccessPolicy;
import com.bko.orchestration.service.FilteringToolCallbackProvider;
import com.bko.stream.OrchestrationStreamService;
import com.bko.files.FileEntry;
import com.bko.files.FileListing;
import com.bko.files.FileService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.bko.entity.OrchestrationSession;
import com.bko.entity.OrchestratorPlanLog;
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
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

@Service
@Slf4j
public class OrchestratorService {

    private static final int MAX_TOOL_CALL_ATTEMPTS = 2;

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
    private final OrchestrationStreamService streamService;
    private final CollaborationStrategyService collaborationStrategyService;
    private final FileService fileService;
    private final ThreadLocal<OrchestrationSession> currentSession = new ThreadLocal<>();

    private record AdvisoryBundle(List<TaskSpec> tasks, List<WorkerResult> results) {
        private AdvisoryBundle() {
            this(List.of(), List.of());
        }
    }

    private record DiscoveryBundle(List<TaskSpec> tasks, List<WorkerResult> results) {
        private DiscoveryBundle() {
            this(List.of(), List.of());
        }
    }

    public OrchestratorService(
            ChatClient chatClient,
            @Qualifier("openAiChatClient") ObjectProvider<ChatClient> openAiChatClientProvider,
            MultiAgentProperties properties,
            @Qualifier("workerExecutor") ExecutorService workerExecutor,
            ToolCallbackProvider toolCallbackProvider,
            FileEditDetectionService fileEditDetectionService,
            OrchestrationPromptService orchestrationPromptService,
            OrchestrationContextService orchestrationContextService,
            JsonProcessingService jsonProcessingService,
            ToolAccessPolicy toolAccessPolicy,
            OrchestrationPersistenceService persistenceService,
            OrchestrationStreamService streamService,
            CollaborationStrategyService collaborationStrategyService,
            FileService fileService) {
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
        this.streamService = streamService;
        this.collaborationStrategyService = collaborationStrategyService;
        this.fileService = fileService;
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
        return orchestrateInternal(userMessage, provider, model, null);
    }

    public OrchestrationResult orchestrateStreaming(String userMessage, String provider, String model, String streamId) {
        return orchestrateInternal(userMessage, provider, model, streamId);
    }

    private OrchestrationResult orchestrateInternal(String userMessage, String provider, String model, @Nullable String streamId) {
        OrchestrationSession session = persistenceService.startSession(userMessage, provider, model);
        currentSession.set(session);
        try {
            if (streamId != null) {
                streamService.emitSession(streamId, session.getId().toString());
                streamService.emitStatus(streamId, "Starting orchestration");
            }
            if (handleCancellation(streamId, session, "Cancelled")) {
                return new OrchestrationResult(new OrchestratorPlan(userMessage, List.of()), List.of(), "Cancelled.");
            }
            boolean requiresEdits = fileEditDetectionService.requiresFileEdits(userMessage);
            if (streamId != null) {
                streamService.emitStatus(streamId, "Selecting roles");
            }
            List<String> initialRoles = selectRoles(userMessage, requiresEdits, null, provider, model);
            List<WorkerResult> advisoryResults = new ArrayList<>();
            List<TaskSpec> advisoryTasks = new ArrayList<>();

            TaskSpec contextTask = buildContextSyncTask(initialRoles);
            String contextSyncContext = null;
            if (contextTask != null) {
                advisoryTasks.add(contextTask);
                if (streamId != null) {
                    streamService.emitTaskStart(streamId, contextTask);
                }
                WorkerResult contextResult = runWorker(session, userMessage, contextTask, false, null, provider, model,
                        false, false, null, streamId);
                advisoryResults.add(contextResult);
                contextSyncContext = orchestrationContextService.buildResultsContext(List.of(contextResult));
            }

            AdvisoryBundle analysisBundle = runAnalysisRounds(session, userMessage, initialRoles, requiresEdits,
                    provider, model, contextSyncContext, streamId);
            if (!analysisBundle.results().isEmpty()) {
                advisoryResults.addAll(analysisBundle.results());
                advisoryTasks.addAll(analysisBundle.tasks());
            }

            TaskSpec designTask = buildDesignTask(userMessage, initialRoles);
            if (designTask != null) {
                advisoryTasks.add(designTask);
                String analysisContext = orchestrationContextService.buildResultsContext(analysisBundle.results());
                String designContext = orchestrationContextService.mergeContexts(contextSyncContext, analysisContext);
                if (streamId != null) {
                    streamService.emitTaskStart(streamId, designTask);
                }
                advisoryResults.add(runWorker(session, userMessage, designTask, requiresEdits, designContext, provider, model,
                        true, false, null, streamId));
            }

            if (!advisoryTasks.isEmpty()) {
                log.info("Advisory tasks scheduled: {}.", advisoryTasks.size());
            }
            String advisoryContext = orchestrationContextService.mergeContexts(
                    contextSyncContext,
                    orchestrationContextService.buildAdvisoryContext(advisoryResults));

            if (streamId != null) {
                streamService.emitStatus(streamId, "Generating plan");
            }
            List<String> executionRoles = selectRoles(userMessage, requiresEdits, advisoryContext, provider, model);
            OrchestratorPlan rawPlan = requestPlan(userMessage, requiresEdits, executionRoles, advisoryContext, provider, model);
            OrchestratorPlan initialPlan = sanitizePlan(rawPlan, userMessage, requiresEdits, executionRoles, true, false);
            if (streamId != null) {
                streamService.emitPlan(streamId, initialPlan);
            }

            var planLog = persistenceService.logPlan(session, initialPlan, true);
            Map<String, TaskLog> taskIndex = new HashMap<>();
            taskIndex.putAll(persistenceService.logTasks(planLog, initialPlan.tasks()));

            List<TaskSpec> allTasks = new ArrayList<>(advisoryTasks);
            List<WorkerResult> allResults = new ArrayList<>(advisoryResults);

            OrchestratorPlan currentPlan = initialPlan;
            int iteration = 0;
            while (iteration < MAX_EXECUTION_ITERATIONS && currentPlan != null && !currentPlan.tasks().isEmpty()) {
                if (handleCancellation(streamId, session, "Cancelled")) {
                    return new OrchestrationResult(new OrchestratorPlan(userMessage, allTasks), allResults, "Cancelled.");
                }
                log.info("Executing plan iteration {} with {} tasks.", iteration + 1, currentPlan.tasks().size());
                if (streamId != null) {
                    streamService.emitStatus(streamId, "Executing tasks (iteration " + (iteration + 1) + ")");
                }
                List<WorkerResult> iterationResults = executePlanTasks(session, userMessage, currentPlan.tasks(),
                        requiresEdits, advisoryContext, allResults, provider, model, taskIndex, streamId);
                allResults.addAll(iterationResults);
                allTasks.addAll(currentPlan.tasks());

                List<FailureDetail> failures = collectFailures(iterationResults, currentPlan.tasks());
                String errorSummary = buildErrorSummary(failures);
                OrchestratorPlan continuationRaw = requestContinuationPlan(userMessage, requiresEdits, executionRoles,
                        advisoryContext, errorSummary, currentPlan, allResults, provider, model);
                OrchestratorPlan continuationPlan = sanitizePlan(continuationRaw, userMessage, requiresEdits,
                        executionRoles, true, true);
                if (continuationPlan.tasks().isEmpty() && !failures.isEmpty()) {
                    continuationPlan = sanitizePlan(buildRetryPlan(userMessage, failures), userMessage, requiresEdits,
                            executionRoles, true, true);
                }
                if (continuationPlan.tasks().isEmpty()) {
                    break;
                }
                var contPlanLog = persistenceService.logPlan(session, continuationPlan, false);
                taskIndex.putAll(persistenceService.logTasks(contPlanLog, continuationPlan.tasks()));
                currentPlan = continuationPlan;
                if (streamId != null) {
                    streamService.emitPlanUpdate(streamId, currentPlan);
                }
                iteration++;
            }

            String objective = (initialPlan != null && StringUtils.hasText(initialPlan.objective()))
                    ? initialPlan.objective()
                    : userMessage;
            OrchestratorPlan finalPlan = new OrchestratorPlan(objective, allTasks);
            if (streamId != null) {
                streamService.emitStatus(streamId, "Synthesizing response");
            }
            String finalAnswer = synthesize(session, userMessage, finalPlan, allResults, provider, model);
            persistenceService.completeSession(session, finalAnswer, "COMPLETED");
            if (streamId != null) {
                streamService.emitFinalAnswer(streamId, finalAnswer);
                streamService.emitRunComplete(streamId, "COMPLETED");
            }
            logSummary();
            return new OrchestrationResult(finalPlan, allResults, finalAnswer);
        } finally {
            currentSession.remove();
        }
    }

    public PlanDraft plan(String userMessage, String provider, String model) {
        return planInternal(userMessage, provider, model, null);
    }

    public PlanDraft planStreaming(String userMessage, String provider, String model, String streamId) {
        return planInternal(userMessage, provider, model, streamId);
    }

    private PlanDraft planInternal(String userMessage, String provider, String model, @Nullable String streamId) {
        OrchestrationSession session = persistenceService.startSession(userMessage, provider, model);
        currentSession.set(session);
        try {
            if (streamId != null) {
                streamService.emitSession(streamId, session.getId().toString());
                streamService.emitStatus(streamId, "Starting discovery");
            }
            if (handleCancellation(streamId, session, "Cancelled")) {
                OrchestratorPlan emptyPlan = new OrchestratorPlan(userMessage, List.of());
                return new PlanDraft("", session.getId().toString(), emptyPlan, List.of(), "CANCELLED");
            }
            boolean requiresEdits = fileEditDetectionService.requiresFileEdits(userMessage);
            DiscoveryBundle discovery = runDiscovery(session, userMessage, provider, model, streamId);
            if (handleCancellation(streamId, session, "Cancelled")) {
                OrchestratorPlan emptyPlan = new OrchestratorPlan(userMessage, List.of());
                return new PlanDraft("", session.getId().toString(), emptyPlan, discovery.results(), "CANCELLED");
            }
            String discoveryContext = orchestrationContextService.buildResultsContext(discovery.results());

            if (streamId != null) {
                streamService.emitStatus(streamId, "Generating task plan");
            }
            if (handleCancellation(streamId, session, "Cancelled")) {
                OrchestratorPlan emptyPlan = new OrchestratorPlan(userMessage, List.of());
                return new PlanDraft("", session.getId().toString(), emptyPlan, discovery.results(), "CANCELLED");
            }
            List<String> selectedRoles = selectRoles(userMessage, requiresEdits, discoveryContext, provider, model);
            OrchestratorPlan rawPlan = requestPlan(userMessage, requiresEdits, selectedRoles, discoveryContext, provider, model);
            OrchestratorPlan sanitized = sanitizePlan(rawPlan, userMessage, requiresEdits, selectedRoles, false, false);
            OrchestratorPlanLog planLog = persistenceService.logPlan(session, sanitized, true);
            persistenceService.logTasks(planLog, sanitized.tasks());
            persistenceService.completeSession(session, null, "AWAITING_APPROVAL");

            PlanDraft draft = new PlanDraft(planLog.getId().toString(), session.getId().toString(),
                    sanitized, discovery.results(), "AWAITING_APPROVAL");
            if (streamId != null) {
                streamService.emitPlanDraft(streamId, draft);
                streamService.emitStatus(streamId, "Awaiting approval");
                streamService.emitRunComplete(streamId, "AWAITING_APPROVAL");
            }
            return draft;
        } finally {
            currentSession.remove();
        }
    }

    public OrchestrationResult executePlan(String planId, @Nullable String feedback, String provider, String model) {
        return executePlanInternal(planId, feedback, provider, model, null);
    }

    public OrchestrationResult executePlanStreaming(String planId, @Nullable String feedback, String provider, String model,
                                                    String streamId) {
        return executePlanInternal(planId, feedback, provider, model, streamId);
    }

    private OrchestrationResult executePlanInternal(String planId, @Nullable String feedback, String provider, String model,
                                                    @Nullable String streamId) {
        OrchestratorPlanLog planLog = persistenceService.findPlanWithTasks(planId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown planId: " + planId));
        OrchestrationSession session = planLog.getSession();
        currentSession.set(session);
        try {
            if (streamId != null) {
                streamService.emitSession(streamId, session.getId().toString());
                streamService.emitStatus(streamId, "Executing approved plan");
            }
            if (handleCancellation(streamId, session, "Cancelled")) {
                OrchestratorPlan cancelledPlan = planFromLog(planLog);
                return new OrchestrationResult(cancelledPlan, List.of(), "Cancelled.");
            }
            String userMessage = session.getUserPrompt();
            if (StringUtils.hasText(feedback)) {
                userMessage = userMessage + "\n\nUser feedback:\n" + feedback.trim();
            }
            boolean requiresEdits = fileEditDetectionService.requiresFileEdits(session.getUserPrompt());
            OrchestratorPlan plan = planFromLog(planLog);
            Map<String, TaskLog> taskIndex = taskIndexFromPlanLog(planLog);
            List<WorkerResult> results = executeApprovedPlanTasks(session, userMessage, plan.tasks(),
                    requiresEdits, provider, model, taskIndex, streamId);
            if (handleCancellation(streamId, session, "Cancelled")) {
                return new OrchestrationResult(plan, results, "Cancelled.");
            }
            String finalAnswer = synthesize(session, userMessage, plan, results, provider, model);
            persistenceService.completeSession(session, finalAnswer, "COMPLETED");
            if (streamId != null) {
                streamService.emitFinalAnswer(streamId, finalAnswer);
                streamService.emitRunComplete(streamId, "COMPLETED");
            }
            logSummary();
            return new OrchestrationResult(plan, results, finalAnswer);
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
                                                     @Nullable String errorSummary,
                                                     OrchestratorPlan plan, List<WorkerResult> results,
                                                     String provider, String model) {
        try {
            String systemPrompt = orchestrationPromptService.executionReviewPrompt(requiresEdits, allowedRoles);
            String normalizedContext = orchestrationContextService.defaultContext(context);
            String planJson = jsonProcessingService.toJson(plan);
            String resultsJson = jsonProcessingService.toJson(results);
            String normalizedErrors = StringUtils.hasText(errorSummary) ? errorSummary : "None.";
            logLlmRequest(PURPOSE_PLAN_REVIEW, null);
            String response = applyTools(getChatRequestSpec(provider, model), ToolAccessPolicy.Phase.ORCHESTRATOR, null)
                    .system(systemPrompt)
                    .user(user -> user.text(EXECUTION_REVIEW_USER_TEMPLATE)
                            .param("input", userMessage)
                            .param("context", normalizedContext)
                            .param("errors", normalizedErrors)
                            .param("plan", planJson)
                            .param("results", resultsJson))
                    .call()
                    .content();
            try {
                persistenceService.logPrompt(currentSession.get(), PURPOSE_PLAN_REVIEW, null, systemPrompt, EXECUTION_REVIEW_USER_TEMPLATE,
                        Map.of("input", userMessage, "context", normalizedContext, "errors", normalizedErrors,
                                "plan", planJson, "results", resultsJson), response);
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
                                .param("errors", normalizedErrors)
                                .param("plan", planJson)
                                .param("results", resultsJson))
                        .call()
                        .content();
                try {
                    persistenceService.logPrompt(currentSession.get(), PURPOSE_PLAN_REVIEW_RETRY, null, retryPrompt, EXECUTION_REVIEW_USER_TEMPLATE,
                            Map.of("input", userMessage, "context", normalizedContext, "errors", normalizedErrors,
                                    "plan", planJson, "results", resultsJson), retryResponse);
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

    private DiscoveryBundle runDiscovery(OrchestrationSession session, String userMessage,
                                         String provider, String model, @Nullable String streamId) {
        TaskSpec discoveryTask = buildDiscoveryTask();
        if (streamId != null) {
            streamService.emitTaskStart(streamId, discoveryTask);
        }
        if (isCancelled(streamId)) {
            WorkerResult cancelled = cancelledResult(discoveryTask, streamId);
            return new DiscoveryBundle(List.of(discoveryTask), List.of(cancelled));
        }
        WorkerResult result = runWorker(session, userMessage, discoveryTask, false, null, provider, model,
                false, true, null, streamId);
        return new DiscoveryBundle(List.of(discoveryTask), List.of(result));
    }

    private TaskSpec buildDiscoveryTask() {
        List<String> roles = normalizedRoles();
        String role = roles.contains(ROLE_ANALYSIS) ? ROLE_ANALYSIS : fallbackRole(roles);
        return new TaskSpec(TASK_ID_DISCOVERY, role, DISCOVERY_TASK_DESCRIPTION, DISCOVERY_TASK_EXPECTED_OUTPUT);
    }

    private AdvisoryBundle runAnalysisRounds(OrchestrationSession session, String userMessage, List<String> selectedRoles,
                                             boolean requiresEdits, String provider, String model, @Nullable String baseContext,
                                             @Nullable String streamId) {
        if (selectedRoles == null || !selectedRoles.contains(ROLE_ANALYSIS)) {
            return new AdvisoryBundle();
        }
        TaskSpec analysisTask = new TaskSpec(TASK_ID_ANALYSIS, ROLE_ANALYSIS,
                ANALYSIS_TASK_DESCRIPTION, ANALYSIS_TASK_EXPECTED_OUTPUT.formatted(ANALYSIS_HANDOFF_SCHEMA));
        if (streamId != null) {
            streamService.emitTaskStart(streamId, analysisTask);
        }
        WorkerResult result = runCollaborativeTask(session, userMessage, analysisTask, requiresEdits,
                baseContext, provider, model, null, streamId);
        return new AdvisoryBundle(List.of(analysisTask), List.of(result));
    }

    private TaskSpec buildDesignTask(String userMessage, List<String> selectedRoles) {
        if (selectedRoles == null || !selectedRoles.contains(ROLE_DESIGN)) {
            return null;
        }
        return new TaskSpec(TASK_ID_DESIGN, ROLE_DESIGN, DESIGN_TASK_DESCRIPTION,
                DESIGN_TASK_EXPECTED_OUTPUT.formatted(DESIGN_HANDOFF_SCHEMA));
    }

    private TaskSpec buildContextSyncTask(List<String> selectedRoles) {
        String role = (selectedRoles != null && selectedRoles.contains(ROLE_ANALYSIS))
                ? ROLE_ANALYSIS
                : ROLE_GENERAL;
        return new TaskSpec(TASK_ID_CONTEXT, role, CONTEXT_SYNC_TASK_DESCRIPTION, CONTEXT_SYNC_TASK_EXPECTED_OUTPUT);
    }

    private WorkerResult runCollaborativeTask(OrchestrationSession session, String userMessage, TaskSpec task, boolean requiresEdits,
                                              @Nullable String baseContext, String provider, String model, @Nullable TaskLog taskLog,
                                              @Nullable String streamId) {
        if (isCancelled(streamId)) {
            return cancelledResult(task, streamId);
        }
        MultiAgentProperties.RoleExecutionConfig exec = properties.getRoleExecutionConfig(task.role());
        int rounds = Math.max(1, exec.getRounds());
        int agents = Math.max(1, exec.getAgents());
        CollaborationStrategy strategy = exec.getCollaborationStrategy();
        List<CollaborationStage> stages = collaborationStrategyService.stagesFor(strategy);
        if (requiresEdits && ROLE_IMPLEMENTER.equals(task.role())
                && stages.stream().noneMatch(CollaborationStage::allowEdits)) {
            log.warn("Collaboration strategy {} for role {} does not allow file edits; outputs will be advisory only.",
                    strategy, task.role());
        }
        String rollingContext = baseContext;
        String finalSummary = "";
        for (int round = 1; round <= rounds; round++) {
            if (isCancelled(streamId)) {
                return cancelledResult(task, streamId);
            }
            for (int stageIndex = 0; stageIndex < stages.size(); stageIndex++) {
                if (isCancelled(streamId)) {
                    return cancelledResult(task, streamId);
                }
                CollaborationStage stage = stages.get(stageIndex);
                boolean finalStage = stageIndex == stages.size() - 1;
                String roundContext = orchestrationContextService.mergeContexts(baseContext, rollingContext);
                List<TaskSpec> roundTasks = new ArrayList<>(agents);
                for (int agent = 1; agent <= agents; agent++) {
                    String id = task.id() + "-r" + round + "-a" + agent + "-" + stage.key();
                    String description = task.description() + " (Round " + round + ", agent " + agent + ", stage " + stage.label() + ")";
                    String expectedOutput = stage.expectedOutput(id, task.expectedOutput());
                    roundTasks.add(new TaskSpec(id, task.role(), description, expectedOutput));
                }
                boolean stageRequiresEdits = requiresEdits && stage.allowEdits();
                List<CompletableFuture<WorkerResult>> futures = roundTasks.stream()
                        .map(subTask -> {
                            if (streamId != null) {
                                streamService.emitTaskStart(streamId, subTask);
                            }
                            return CompletableFuture.supplyAsync(
                                            () -> runWorker(session, userMessage, subTask, stageRequiresEdits, roundContext, provider, model,
                                                    false, false, taskLog, streamId),
                                            workerExecutor)
                                    .orTimeout(properties.getWorkerTimeout().toSeconds(), TimeUnit.SECONDS)
                                    .exceptionally(ex -> {
                                        WorkerResult failed = new WorkerResult(subTask.id(), subTask.role(),
                                                WORKER_FAILED_MESSAGE + ex.getMessage());
                                        if (streamId != null) {
                                            streamService.emitTaskOutput(streamId, failed);
                                            streamService.emitTaskComplete(streamId, failed);
                                        }
                                        return failed;
                                    });
                        })
                        .toList();
                if (isCancelled(streamId)) {
                    futures.forEach(future -> future.cancel(true));
                    return cancelledResult(task, streamId);
                }
                List<WorkerResult> roundResults = futures.stream().map(CompletableFuture::join).toList();
                String summary = collaborateRound(userMessage, task, round, stage, strategy, finalStage, roundResults, provider, model);
                finalSummary = summary;
                rollingContext = orchestrationContextService.mergeContexts(rollingContext, summary);
                TaskSpec summaryTask = new TaskSpec(task.id() + "-r" + round + "-" + stage.key() + "-summary", task.role(),
                        "Collaboration summary for round " + round + " (" + stage.label() + ")", "Summarize best findings.");
                WorkerResult summaryResult = new WorkerResult(summaryTask.id(), summaryTask.role(), summary);
                if (streamId != null) {
                    streamService.emitTaskStart(streamId, summaryTask);
                    streamService.emitTaskOutput(streamId, summaryResult);
                    streamService.emitTaskComplete(streamId, summaryResult);
                }
            }
        }
        WorkerResult finalResult = new WorkerResult(task.id(), task.role(), finalSummary);
        if (streamId != null) {
            streamService.emitTaskOutput(streamId, finalResult);
            streamService.emitTaskComplete(streamId, finalResult);
        }
        try {
            Map<String, String> params = Map.of(
                    "input", userMessage,
                    "context", orchestrationContextService.defaultContext(baseContext),
                    "task", task.description(),
                    "expectedOutput", task.expectedOutput()
            );
            persistenceService.logPrompt(session, PURPOSE_WORKER_TASK, task.role(),
                    orchestrationPromptService.workerSystemPrompt(task.role(), requiresEdits, false),
                    WORKER_USER_TEMPLATE, params, finalSummary);
            persistenceService.logWorkerResult(session, taskLog, task.role(), finalSummary);
        } catch (Exception ignore) { }
        return finalResult;
    }

    private String collaborateRound(String userMessage, TaskSpec task, int round, CollaborationStage stage,
                                    CollaborationStrategy strategy, boolean finalStage, List<WorkerResult> results,
                                    String provider, String model) {
        String systemPrompt = orchestrationPromptService.collaborationSystemPrompt(task.role(), strategy, stage, finalStage);
        String resultsJson = jsonProcessingService.toJson(results);
        String output = applyTools(getChatRequestSpec(provider, model), ToolAccessPolicy.Phase.SYNTHESIS, null)
                .system(systemPrompt)
                .user(user -> user.text(COLLABORATION_USER_TEMPLATE)
                        .param("input", userMessage)
                        .param("task", task.description())
                        .param("round", String.valueOf(round))
                        .param("strategy", strategy != null ? strategy.label() : "Simple summary")
                        .param("stage", stage != null ? stage.label() : "Summary")
                        .param("results", resultsJson))
                .call()
                .content();
        return output != null ? output : "";
    }

    private List<WorkerResult> executePlanTasks(OrchestrationSession session, String userMessage, List<TaskSpec> tasks, boolean requiresEdits,
                                                String advisoryContext, List<WorkerResult> priorResults, String provider, String model,
                                                Map<String, TaskLog> taskIndex, @Nullable String streamId) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        if (isCancelled(streamId)) {
            return List.of();
        }
        boolean contextAlreadyRun = priorResults != null && priorResults.stream()
                .anyMatch(result -> TASK_ID_CONTEXT.equalsIgnoreCase(result.taskId()));
        List<TaskSpec> effectiveTasks = contextAlreadyRun
                ? tasks.stream().filter(task -> !TASK_ID_CONTEXT.equalsIgnoreCase(task.id())).toList()
                : tasks;
        if (effectiveTasks.isEmpty()) {
            return List.of();
        }
        long totalExecuted = taskExecutedCount.addAndGet(effectiveTasks.size());
        log.info("Executing {} plan tasks. Total tasks executed so far={}.", effectiveTasks.size(), totalExecuted);
        Duration timeout = properties.getWorkerTimeout();
        if (requiresEdits) {
            List<TaskSpec> engineeringTasks = effectiveTasks.stream()
                    .filter(task -> ROLE_ENGINEERING.equals(task.role()))
                    .toList();
            List<TaskSpec> implementerTasks = effectiveTasks.stream()
                    .filter(task -> ROLE_IMPLEMENTER.equals(task.role()))
                    .toList();
            List<TaskSpec> otherTasks = effectiveTasks.stream()
                    .filter(task -> !ROLE_ENGINEERING.equals(task.role()) && !ROLE_IMPLEMENTER.equals(task.role()))
                    .toList();
            List<WorkerResult> results = new ArrayList<>(effectiveTasks.size());
            String sharedContext = orchestrationContextService.buildResultsContext(priorResults);
            String engineeringContext = orchestrationContextService.buildResultsContext(
                    orchestrationContextService.filterResultsByRole(priorResults, Set.of(ROLE_ENGINEERING)));
            for (TaskSpec task : engineeringTasks) {
                if (isCancelled(streamId)) {
                    return results;
                }
                String taskContext = engineeringContext;
                TaskLog taskLog = taskIndex.get(task.id());
                if (streamId != null) {
                    streamService.emitTaskStart(streamId, task);
                }
                WorkerResult result = CompletableFuture
                        .supplyAsync(() -> runCollaborativeTask(session, userMessage, task, true, taskContext, provider, model, taskLog, streamId), workerExecutor)
                        .orTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                        .exceptionally(ex -> {
                            WorkerResult failed = new WorkerResult(task.id(), task.role(),
                                    WORKER_FAILED_MESSAGE + ex.getMessage());
                            if (streamId != null) {
                                streamService.emitTaskOutput(streamId, failed);
                                streamService.emitTaskComplete(streamId, failed);
                            }
                            return failed;
                        })
                        .join();
                results.add(result);
                engineeringContext = orchestrationContextService.mergeContexts(engineeringContext,
                        orchestrationContextService.buildResultsContext(List.of(result)));
            }
            if (!otherTasks.isEmpty()) {
                if (isCancelled(streamId)) {
                    return results;
                }
                String discussionContext = orchestrationContextService.mergeContexts(sharedContext,
                        orchestrationContextService.buildResultsContext(results));
                List<CompletableFuture<WorkerResult>> futures = otherTasks.stream()
                        .map(task -> {
                            TaskLog tl = taskIndex.get(task.id());
                            if (streamId != null) {
                                streamService.emitTaskStart(streamId, task);
                            }
                            return CompletableFuture.supplyAsync(() -> runCollaborativeTask(session, userMessage, task, true, discussionContext, provider, model, tl, streamId), workerExecutor)
                                    .orTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                                    .exceptionally(ex -> {
                                        WorkerResult failed = new WorkerResult(task.id(), task.role(),
                                                WORKER_FAILED_MESSAGE + ex.getMessage());
                                        if (streamId != null) {
                                            streamService.emitTaskOutput(streamId, failed);
                                            streamService.emitTaskComplete(streamId, failed);
                                        }
                                        return failed;
                                    });
                        })
                        .toList();
                if (isCancelled(streamId)) {
                    futures.forEach(future -> future.cancel(true));
                    return results;
                }
                results.addAll(futures.stream().map(CompletableFuture::join).toList());
            }
            if (implementerTasks.isEmpty()) {
                return results;
            }
            String implementationContext = orchestrationContextService.mergeContexts(sharedContext,
                    orchestrationContextService.buildResultsContext(results));
            for (TaskSpec task : implementerTasks) {
                if (isCancelled(streamId)) {
                    return results;
                }
                String taskContext = implementationContext;
                TaskLog taskLog = taskIndex.get(task.id());
                if (streamId != null) {
                    streamService.emitTaskStart(streamId, task);
                }
                WorkerResult result = CompletableFuture
                        .supplyAsync(() -> runCollaborativeTask(session, userMessage, task, true, taskContext, provider, model, taskLog, streamId), workerExecutor)
                        .orTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                        .exceptionally(ex -> {
                            WorkerResult failed = new WorkerResult(task.id(), task.role(),
                                    WORKER_FAILED_MESSAGE + ex.getMessage());
                            if (streamId != null) {
                                streamService.emitTaskOutput(streamId, failed);
                                streamService.emitTaskComplete(streamId, failed);
                            }
                            return failed;
                        })
                        .join();
                results.add(result);
                implementationContext = orchestrationContextService.mergeContexts(implementationContext,
                        orchestrationContextService.buildResultsContext(List.of(result)));
            }
            return results;
        }

        String context = orchestrationContextService.buildResultsContext(priorResults);
        List<CompletableFuture<WorkerResult>> futures = effectiveTasks.stream()
                .map(task -> {
                    TaskLog tl = taskIndex.get(task.id());
                    if (streamId != null) {
                        streamService.emitTaskStart(streamId, task);
                    }
                    return CompletableFuture.supplyAsync(() -> runCollaborativeTask(session, userMessage, task, false, context, provider, model, tl, streamId), workerExecutor)
                            .orTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                            .exceptionally(ex -> {
                                WorkerResult failed = new WorkerResult(task.id(), task.role(),
                                        WORKER_FAILED_MESSAGE + ex.getMessage());
                                if (streamId != null) {
                                    streamService.emitTaskOutput(streamId, failed);
                                    streamService.emitTaskComplete(streamId, failed);
                                }
                                return failed;
                        });
                })
                .toList();
        if (isCancelled(streamId)) {
            futures.forEach(future -> future.cancel(true));
            return List.of();
        }
        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    private List<WorkerResult> executeApprovedPlanTasks(OrchestrationSession session, String userMessage, List<TaskSpec> tasks,
                                                        boolean requiresEdits, String provider, String model,
                                                        Map<String, TaskLog> taskIndex, @Nullable String streamId) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        if (isCancelled(streamId)) {
            return List.of();
        }
        List<TaskSpec> effectiveTasks = tasks.stream()
                .filter(task -> !TASK_ID_CONTEXT.equalsIgnoreCase(task.id()))
                .filter(task -> !TASK_ID_DISCOVERY.equalsIgnoreCase(task.id()))
                .toList();
        if (effectiveTasks.isEmpty()) {
            return List.of();
        }
        long totalExecuted = taskExecutedCount.addAndGet(effectiveTasks.size());
        log.info("Executing {} approved plan tasks. Total tasks executed so far={}.", effectiveTasks.size(), totalExecuted);
        Duration timeout = properties.getWorkerTimeout();

        List<TaskSpec> implementerTasks = effectiveTasks.stream()
                .filter(task -> ROLE_IMPLEMENTER.equals(task.role()))
                .toList();
        List<TaskSpec> advisoryTasks = effectiveTasks.stream()
                .filter(task -> !ROLE_IMPLEMENTER.equals(task.role()))
                .toList();

        List<WorkerResult> results = new ArrayList<>(effectiveTasks.size());
        if (!advisoryTasks.isEmpty()) {
            if (isCancelled(streamId)) {
                return results;
            }
            List<CompletableFuture<WorkerResult>> futures = advisoryTasks.stream()
                    .map(task -> {
                        TaskLog tl = taskIndex.get(task.id());
                        boolean taskRequiresEdits = false;
                        if (streamId != null) {
                            streamService.emitTaskStart(streamId, task);
                        }
                        return CompletableFuture.supplyAsync(
                                        () -> runWorker(session, userMessage, task, taskRequiresEdits, null, provider, model,
                                                false, false, tl, streamId),
                                        workerExecutor)
                                .orTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                                .exceptionally(ex -> {
                                    WorkerResult failed = new WorkerResult(task.id(), task.role(),
                                            WORKER_FAILED_MESSAGE + ex.getMessage());
                                    if (streamId != null) {
                                        streamService.emitTaskOutput(streamId, failed);
                                        streamService.emitTaskComplete(streamId, failed);
                                    }
                                    return failed;
                                });
                    })
                    .toList();
            if (isCancelled(streamId)) {
                futures.forEach(future -> future.cancel(true));
                return results;
            }
            results.addAll(futures.stream().map(CompletableFuture::join).toList());
        }

        if (!implementerTasks.isEmpty()) {
            if (isCancelled(streamId)) {
                return results;
            }
            String implementationContext = orchestrationContextService.buildResultsContext(results);
            for (TaskSpec task : implementerTasks) {
                if (isCancelled(streamId)) {
                    return results;
                }
                TaskLog taskLog = taskIndex.get(task.id());
                String taskContext = implementationContext;
                boolean taskRequiresEdits = requiresEdits;
                if (streamId != null) {
                    streamService.emitTaskStart(streamId, task);
                }
                WorkerResult result = CompletableFuture
                        .supplyAsync(() -> runWorker(session, userMessage, task, taskRequiresEdits, taskContext, provider, model,
                                false, false, taskLog, streamId), workerExecutor)
                        .orTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                        .exceptionally(ex -> {
                            WorkerResult failed = new WorkerResult(task.id(), task.role(),
                                    WORKER_FAILED_MESSAGE + ex.getMessage());
                            if (streamId != null) {
                                streamService.emitTaskOutput(streamId, failed);
                                streamService.emitTaskComplete(streamId, failed);
                            }
                            return failed;
                        })
                        .join();
                results.add(result);
                implementationContext = orchestrationContextService.mergeContexts(implementationContext,
                        orchestrationContextService.buildResultsContext(List.of(result)));
            }
        }
        return results;
    }

    private WorkerResult runWorker(OrchestrationSession session, String userMessage, TaskSpec task, boolean requiresEdits,
                                   @Nullable String context, String provider, String model, boolean includeHandoffSchema,
                                   boolean requireToolCalls, @Nullable TaskLog taskLog, @Nullable String streamId) {
        if (isCancelled(streamId)) {
            return cancelledResult(task, streamId);
        }
        String normalizedContext = orchestrationContextService.defaultContext(context);
        String systemPrompt = orchestrationPromptService.workerSystemPrompt(task.role(), requiresEdits, includeHandoffSchema);
        WorkerCallResult callResult = runWorkerPrompt(systemPrompt, userMessage, task, normalizedContext,
                provider, model, ToolAccessPolicy.Phase.WORKER);
        logToolCalls(session, taskLog, task, callResult.audit());
        if (requireToolCalls && callResult.toolCallCount() == 0) {
            int attempts = 1;
            while (attempts < MAX_TOOL_CALL_ATTEMPTS && callResult.toolCallCount() == 0) {
                String retryPrompt = systemPrompt + """

                        Your last response did not call any MCP filesystem tools. Tool calls are required for this task.
                        Use list_directory/read_file to inspect the repository, then return your findings.
                        """;
                callResult = runWorkerPrompt(retryPrompt, userMessage, task, normalizedContext,
                        provider, model, ToolAccessPolicy.Phase.WORKER);
                logToolCalls(session, taskLog, task, callResult.audit());
                attempts++;
            }
            if (callResult.toolCallCount() == 0) {
                log.warn("Task {} returned without tool calls after {} attempts.", task.id(), MAX_TOOL_CALL_ATTEMPTS);
            }
        }
        if (requiresEdits && ROLE_IMPLEMENTER.equals(task.role()) && callResult.toolCallCount() == 0) {
            int attempts = 1;
            while (attempts < MAX_TOOL_CALL_ATTEMPTS && callResult.toolCallCount() == 0) {
                String retryPrompt = systemPrompt + """

                        Your last response did not call any MCP filesystem tools. Tool calls are mandatory for this task.
                        Use MCP tools to read and write files, then respond with a concise summary of changes and next steps.
                        """;
                callResult = runWorkerPrompt(retryPrompt, userMessage, task, normalizedContext,
                        provider, model, ToolAccessPolicy.Phase.WORKER);
                logToolCalls(session, taskLog, task, callResult.audit());
                attempts++;
            }
            if (callResult.toolCallCount() == 0) {
                log.warn("Implementer task {} returned without tool calls after {} attempts.",
                        task.id(), MAX_TOOL_CALL_ATTEMPTS);
            }
        }
        if (requiresEdits && ROLE_IMPLEMENTER.equals(task.role()) && callResult.writeCallCount() == 0) {
            int attempts = 1;
            while (attempts < MAX_TOOL_CALL_ATTEMPTS && callResult.writeCallCount() == 0) {
                String retryPrompt = systemPrompt + """

                        Your last response did not call the write_file tool. File edits are mandatory for implementer tasks.
                        You must call write_file to apply the changes, then respond with a concise summary and next steps.
                        """;
                callResult = runWorkerPrompt(retryPrompt, userMessage, task, normalizedContext,
                        provider, model, ToolAccessPolicy.Phase.WORKER);
                logToolCalls(session, taskLog, task, callResult.audit());
                attempts++;
            }
            if (callResult.writeCallCount() == 0) {
                log.warn("Implementer task {} returned without write_file calls after {} attempts.",
                        task.id(), MAX_TOOL_CALL_ATTEMPTS);
            }
        }
        String output = callResult.output();
        WorkerResult result = new WorkerResult(task.id(), task.role(), output);
        if (streamId != null) {
            streamService.emitTaskOutput(streamId, result);
            streamService.emitTaskComplete(streamId, result);
        }
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
        return result;
    }

    private void logToolCalls(OrchestrationSession session, @Nullable TaskLog taskLog, TaskSpec task, @Nullable ToolCallAudit audit) {
        if (audit == null) {
            return;
        }
        for (ToolCallRecord record : audit.snapshot()) {
            try {
                persistenceService.logToolCall(session, taskLog, task.role(), record.name(), record.input(), record.output());
            } catch (Exception ignore) { }
        }
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

    private record FailureDetail(TaskSpec task, String reason) {
    }

    private List<FailureDetail> collectFailures(List<WorkerResult> results, List<TaskSpec> tasks) {
        if (results == null || results.isEmpty() || tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        Map<String, TaskSpec> taskIndex = new HashMap<>();
        for (TaskSpec task : tasks) {
            taskIndex.put(task.id(), task);
        }
        List<FailureDetail> failures = new ArrayList<>();
        for (WorkerResult result : results) {
            if (!isFailureOutput(result.output())) {
                continue;
            }
            TaskSpec task = taskIndex.get(result.taskId());
            String reason = extractFailureReason(result.output());
            if (task != null) {
                failures.add(new FailureDetail(task, reason));
            }
        }
        return failures;
    }

    private boolean isFailureOutput(@Nullable String output) {
        if (!StringUtils.hasText(output)) {
            return false;
        }
        String normalized = output.toLowerCase(Locale.ROOT);
        return normalized.startsWith(WORKER_FAILED_MESSAGE.toLowerCase(Locale.ROOT))
                || normalized.contains("tool error:");
    }

    private String extractFailureReason(String output) {
        if (!StringUtils.hasText(output)) {
            return "";
        }
        String trimmed = output.trim();
        if (trimmed.startsWith(WORKER_FAILED_MESSAGE)) {
            return trimmed.substring(WORKER_FAILED_MESSAGE.length()).trim();
        }
        return trimmed;
    }

    private String buildErrorSummary(List<FailureDetail> failures) {
        if (failures == null || failures.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (FailureDetail failure : failures) {
            sb.append("- [").append(failure.task().role()).append(" ")
                    .append(failure.task().id()).append("] ")
                    .append(failure.reason()).append("\n");
        }
        return sb.toString().trim();
    }

    private OrchestratorPlan buildRetryPlan(String objective, List<FailureDetail> failures) {
        List<TaskSpec> tasks = new ArrayList<>();
        for (FailureDetail failure : failures) {
            TaskSpec task = failure.task();
            String description = task.description();
            if (StringUtils.hasText(failure.reason())) {
                description = description + " (Retry and resolve error: " + failure.reason() + ")";
            }
            tasks.add(new TaskSpec(task.id(), task.role(), description, task.expectedOutput()));
        }
        return new OrchestratorPlan(objective, tasks);
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
        List<TaskSpec> sanitized = new ArrayList<>(maxTasks + 1);
        List<String> normalizedRoles = normalizeAllowedRoles(allowedRoles);
        Set<String> seenSignatures = new LinkedHashSet<>();
        for (int index = 0; index < maxTasks; index++) {
            TaskSpec task = incomingTasks.get(index);
            String role = normalizeRole(task.role(), normalizedRoles);
            if (excludeAdvisory && ADVISORY_ROLES.contains(role)) {
                continue;
            }
            String id = StringUtils.hasText(task.id()) ? task.id() : TASK_PREFIX + (index + 1);
            if (TASK_ID_CONTEXT.equalsIgnoreCase(id) || TASK_ID_DISCOVERY.equalsIgnoreCase(id)) {
                continue;
            }
            String description = StringUtils.hasText(task.description()) ? task.description() : userMessage;
            String expectedOutput = StringUtils.hasText(task.expectedOutput())
                    ? task.expectedOutput()
                    : DEFAULT_EXPECTED_OUTPUT;
            if (requiresEdits) {
                boolean canEdit = ROLE_IMPLEMENTER.equals(role);
                expectedOutput = fileEditDetectionService.appendFileEditInstruction(expectedOutput, canEdit);
            }
            TaskSpec normalizedTask = new TaskSpec(id, role, description, expectedOutput);
            String signature = normalizeTaskSignature(role, description);
            if (seenSignatures.contains(signature)) {
                continue;
            }
            seenSignatures.add(signature);
            sanitized.add(normalizedTask);
        }
        if (requiresEdits && sanitized.stream().noneMatch(task -> ROLE_IMPLEMENTER.equals(task.role()))) {
            sanitized.add(new TaskSpec(TASK_ID_IMPLEMENTATION, ROLE_IMPLEMENTER, userMessage,
                    fileEditDetectionService.appendFileEditInstruction(DEFAULT_IMPLEMENTATION_INSTRUCTION, true)));
        }
        if (sanitized.isEmpty()) {
            return allowEmpty ? new OrchestratorPlan(objective, List.of())
                    : defaultPlan(userMessage, requiresEdits, allowedRoles);
        }
        return new OrchestratorPlan(objective, sanitized);
    }

    private String normalizeTaskSignature(String role, String description) {
        String normalizedRole = StringUtils.hasText(role) ? role.trim().toLowerCase(Locale.ROOT) : "";
        String normalizedDescription = StringUtils.hasText(description)
                ? description.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT)
                : "";
        return normalizedRole + "::" + normalizedDescription;
    }

    private OrchestratorPlan defaultPlan(String userMessage, boolean requiresEdits, List<String> allowedRoles) {
        List<String> normalizedRoles = normalizeAllowedRoles(allowedRoles);
        String role = requiresEdits
                ? (normalizedRoles.contains(ROLE_IMPLEMENTER)
                        ? ROLE_IMPLEMENTER
                        : (normalizedRoles.contains(ROLE_ENGINEERING) ? ROLE_ENGINEERING : fallbackRole(normalizedRoles)))
                : fallbackRole(normalizedRoles);
        String expectedOutput = DEFAULT_COMPLETE_RESPONSE_INSTRUCTION;
        if (requiresEdits) {
            expectedOutput = fileEditDetectionService.appendFileEditInstruction(expectedOutput, ROLE_IMPLEMENTER.equals(role));
        }
        TaskSpec fallback = new TaskSpec(TASK_ID_FALLBACK, role, userMessage, expectedOutput);
        return new OrchestratorPlan(userMessage, List.of(fallback));
    }

    private OrchestratorPlan planFromLog(OrchestratorPlanLog planLog) {
        String objective = StringUtils.hasText(planLog.getObjective())
                ? planLog.getObjective()
                : (planLog.getSession() != null ? planLog.getSession().getUserPrompt() : "");
        List<TaskLog> tasks = planLog.getTasks() == null ? List.of() : planLog.getTasks();
        List<TaskLog> ordered = tasks.stream()
                .sorted(java.util.Comparator.comparing(TaskLog::getCreatedAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .toList();
        List<TaskSpec> specs = new ArrayList<>(ordered.size());
        int index = 1;
        for (TaskLog taskLog : ordered) {
            String id = StringUtils.hasText(taskLog.getTaskIdAlias()) ? taskLog.getTaskIdAlias() : TASK_PREFIX + index;
            String role = StringUtils.hasText(taskLog.getRole()) ? taskLog.getRole() : ROLE_GENERAL;
            String description = StringUtils.hasText(taskLog.getDescription()) ? taskLog.getDescription() : objective;
            String expectedOutput = StringUtils.hasText(taskLog.getExpectedOutput())
                    ? taskLog.getExpectedOutput()
                    : DEFAULT_EXPECTED_OUTPUT;
            specs.add(new TaskSpec(id, role, description, expectedOutput));
            index++;
        }
        return new OrchestratorPlan(objective, specs);
    }

    private Map<String, TaskLog> taskIndexFromPlanLog(OrchestratorPlanLog planLog) {
        if (planLog.getTasks() == null || planLog.getTasks().isEmpty()) {
            return Map.of();
        }
        Map<String, TaskLog> index = new HashMap<>();
        int counter = 1;
        for (TaskLog taskLog : planLog.getTasks()) {
            String id = StringUtils.hasText(taskLog.getTaskIdAlias()) ? taskLog.getTaskIdAlias() : TASK_PREFIX + counter;
            index.put(id, taskLog);
            counter++;
        }
        return index;
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
        if (requiresEdits) {
            if (!roles.contains(ROLE_ENGINEERING) && available.contains(ROLE_ENGINEERING)) {
                roles.add(ROLE_ENGINEERING);
            }
            if (!roles.contains(ROLE_IMPLEMENTER)) {
                if (available.contains(ROLE_IMPLEMENTER)) {
                    roles.add(ROLE_IMPLEMENTER);
                } else if (available.contains(ROLE_ENGINEERING)) {
                    roles.add(ROLE_ENGINEERING);
                } else {
                    roles.add(fallbackRole(available));
                }
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
        return applyTools(prompt, phase, role, null);
    }

    private ChatClient.ChatClientRequestSpec applyTools(ChatClient.ChatClientRequestSpec prompt, ToolAccessPolicy.Phase phase,
                                                        @Nullable String role, @Nullable ToolCallAudit audit) {
        if (toolCallbackProvider == null) {
            log.warn("Tool callbacks are not configured. phase={}, role={}", phase, role);
            return prompt;
        }
        ToolCallback[] rawCallbacks = toolCallbackProvider.getToolCallbacks();
        if (rawCallbacks == null || rawCallbacks.length == 0) {
            log.warn("No tool callbacks registered from provider. phase={}, role={}", phase, role);
        }
        var allowed = toolAccessPolicy.allowedToolNames(phase, role);
        ToolCallbackProvider filtered = new FilteringToolCallbackProvider(toolCallbackProvider, allowed);
        ToolCallbackProvider effective = audit == null ? filtered : auditedToolCallbackProvider(filtered, audit);
        ToolCallback[] callbacks = effective.getToolCallbacks();
        if (callbacks == null || callbacks.length == 0) {
            if (!allowed.isEmpty()) {
                log.warn("No tool callbacks available after filtering. phase={}, role={}, allowed={}, available={}",
                        phase, role, allowed, describeToolNames(rawCallbacks));
            }
        } else if (log.isDebugEnabled()) {
            log.debug("Tool callbacks available. phase={}, role={}, tools={}",
                    phase, role, describeToolNames(callbacks));
        }
        return prompt.toolCallbacks(effective);
    }

    private WorkerCallResult runWorkerPrompt(String systemPrompt, String userMessage, TaskSpec task, String normalizedContext,
                                             String provider, String model, ToolAccessPolicy.Phase phase) {
        ToolCallAudit audit = new ToolCallAudit(task.role(), task.id());
        logLlmRequest(PURPOSE_WORKER_TASK, task.role());
        String output = applyTools(getChatRequestSpec(provider, model), phase, task.role(), audit)
                .system(systemPrompt)
                .user(user -> user.text(WORKER_USER_TEMPLATE)
                        .param("input", userMessage)
                        .param("context", normalizedContext)
                        .param("task", task.description())
                        .param("expectedOutput", task.expectedOutput()))
                .call()
                .content();
        return new WorkerCallResult(output == null ? "" : output, audit);
    }

    private ToolCallbackProvider auditedToolCallbackProvider(ToolCallbackProvider provider, ToolCallAudit audit) {
        ToolCallback[] callbacks = provider.getToolCallbacks();
        if (callbacks == null || callbacks.length == 0) {
            return provider;
        }
        ToolCallback[] wrapped = Arrays.stream(callbacks)
                .map(callback -> new AuditedToolCallback(callback, audit, fileService))
                .toArray(ToolCallback[]::new);
        return ToolCallbackProvider.from(wrapped);
    }

    private static List<String> describeToolNames(ToolCallback[] callbacks) {
        if (callbacks == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (ToolCallback cb : callbacks) {
            String name = extractToolName(cb);
            if (StringUtils.hasText(name)) {
                names.add(name);
            }
        }
        return names;
    }

    private static String extractToolName(@Nullable ToolCallback callback) {
        if (callback == null) {
            return "";
        }
        String fromDef = reflectName(callback.getToolDefinition());
        if (StringUtils.hasText(fromDef)) {
            return fromDef;
        }
        String fromMeta = reflectName(callback.getToolMetadata());
        if (StringUtils.hasText(fromMeta)) {
            return fromMeta;
        }
        try {
            java.lang.reflect.Method m = callback.getClass().getMethod("getName");
            Object value = m.invoke(callback);
            if (value instanceof String name && StringUtils.hasText(name)) {
                return name;
            }
        } catch (Exception ignore) {
            // ignore reflection failures
        }
        try {
            java.lang.reflect.Method m = callback.getClass().getMethod("name");
            Object value = m.invoke(callback);
            if (value instanceof String name && StringUtils.hasText(name)) {
                return name;
            }
        } catch (Exception ignore) {
            // ignore reflection failures
        }
        return "";
    }

    private static String reflectName(@Nullable Object target) {
        if (target == null) {
            return "";
        }
        for (String method : java.util.List.of("getName", "name", "id")) {
            try {
                java.lang.reflect.Method m = target.getClass().getMethod(method);
                Object value = m.invoke(target);
                if (value instanceof String name && StringUtils.hasText(name)) {
                    return name;
                }
            } catch (Exception ignore) {
                // ignore reflection failures
            }
        }
        return "";
    }

    private record WorkerCallResult(String output, ToolCallAudit audit) {
        int toolCallCount() {
            return audit == null ? 0 : audit.count();
        }

        int writeCallCount() {
            return audit == null ? 0 : audit.writeCount();
        }
    }

    private record ToolCallRecord(String name, String input, String output) {
    }

    private static final class ToolCallAudit {
        private static final int MAX_SNIPPET = 2000;
        private final AtomicLong count = new AtomicLong();
        private final java.util.List<ToolCallRecord> calls = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        private final String role;
        private final String taskId;

        private ToolCallAudit(@Nullable String role, @Nullable String taskId) {
            this.role = role;
            this.taskId = taskId;
        }

        void recordCall(@Nullable String name, @Nullable String input, @Nullable String output) {
            count.incrementAndGet();
            String safeName = StringUtils.hasText(name) ? name : "unknown";
            ToolCallRecord record = new ToolCallRecord(safeName, truncate(input), truncate(output));
            calls.add(record);
            OrchestratorService.log.info("Tool call: name={}, role={}, taskId={}, inputSnippet={}",
                    safeName, role, taskId, truncate(input));
        }

        int count() {
            long value = count.get();
            return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
        }

        int writeCount() {
            int total = 0;
            for (ToolCallRecord record : snapshot()) {
                String name = record.name();
                if (!StringUtils.hasText(name)) {
                    continue;
                }
                String normalized = name.toLowerCase(Locale.ROOT);
                if ("write_file".equals(normalized)
                        || normalized.endsWith(".write_file")
                        || normalized.endsWith("/write_file")
                        || normalized.endsWith(":write_file")) {
                    total++;
                }
            }
            return total;
        }

        java.util.List<ToolCallRecord> snapshot() {
            synchronized (calls) {
                return new java.util.ArrayList<>(calls);
            }
        }

        private String truncate(@Nullable String value) {
            if (!StringUtils.hasText(value)) {
                return "";
            }
            String normalized = value.replace("\r", " ").replace("\n", " ").trim();
            if (normalized.length() <= MAX_SNIPPET) {
                return normalized;
            }
            return normalized.substring(0, MAX_SNIPPET) + "...";
        }
    }

    private static final class AuditedToolCallback implements ToolCallback {
        private final ToolCallback delegate;
        private final ToolCallAudit audit;
        private final FileService fileService;
        private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

        private AuditedToolCallback(ToolCallback delegate, ToolCallAudit audit, FileService fileService) {
            this.delegate = delegate;
            this.audit = audit;
            this.fileService = fileService;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return delegate.getToolMetadata();
        }

        @Override
        public String call(String input) {
            return executeWithAudit(input, () -> delegate.call(input));
        }

        @Override
        public String call(String input, ToolContext toolContext) {
            return executeWithAudit(input, () -> delegate.call(input, toolContext));
        }

        private String resolveToolName() {
            String fromDef = reflectName(delegate.getToolDefinition());
            if (StringUtils.hasText(fromDef)) {
                return fromDef;
            }
            String fromMeta = reflectName(delegate.getToolMetadata());
            if (StringUtils.hasText(fromMeta)) {
                return fromMeta;
            }
            ToolDefinition def = delegate.getToolDefinition();
            return def != null ? def.toString() : "unknown";
        }

        private String executeWithAudit(String input, java.util.concurrent.Callable<String> call) {
            String toolName = resolveToolName();
            try {
                String output = call.call();
                audit.recordCall(toolName, input, output);
                return output;
            } catch (Exception ex) {
                String fallback = attemptReadFallback(toolName, input, ex);
                if (fallback != null) {
                    audit.recordCall(toolName, input, fallback);
                    return fallback;
                }
                fallback = attemptWriteFallback(toolName, input, ex);
                if (fallback != null) {
                    audit.recordCall(toolName, input, fallback);
                    return fallback;
                }
                if (ex instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new RuntimeException(ex);
            }
        }

        private String attemptWriteFallback(String toolName, String input, Exception ex) {
            if (!isWriteTool(toolName)) {
                return null;
            }
            String message = ex.getMessage() == null ? "" : ex.getMessage();
            if (!message.toLowerCase(Locale.ROOT).contains("parent directory does not exist")) {
                return null;
            }
            try {
                String path = extractPathFromInput(input);
                if (!StringUtils.hasText(path)) {
                    return null;
                }
                com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(input);
                String content = node.hasNonNull("content") ? node.get("content").asText() : "";
                fileService.write(path, content);
                log.warn("write_file fallback succeeded by creating parent directories. path={}", path);
                return fallbackJsonResponse("Fallback write_file: created parent directories and wrote file.");
            } catch (Exception fallbackEx) {
                log.warn("write_file fallback failed: {}", fallbackEx.getMessage());
                return null;
            }
        }

        private String attemptReadFallback(String toolName, String input, Exception ex) {
            if (!isReadTool(toolName) && !isListTool(toolName)) {
                return null;
            }
            String message = ex.getMessage() == null ? "" : ex.getMessage();
            String normalized = message.toLowerCase(Locale.ROOT);
            if (!(normalized.contains("no such file") || normalized.contains("not found") || normalized.contains("enoent"))) {
                return null;
            }
            String hint = "";
            try {
                String path = extractPathFromInput(input);
                if (!StringUtils.hasText(path)) {
                    path = extractPathFromMessage(message);
                }
                if (StringUtils.hasText(path)) {
                    hint = buildDirectoryHint(path);
                }
            } catch (Exception ignore) {
                // ignore hint failures
            }
            log.warn("Recoverable tool error for {}: {}", toolName, message);
            String combined = hint.isBlank() ? "Tool error: " + message : "Tool error: " + message + " " + hint;
            return fallbackJsonResponse(combined);
        }

        private String fallbackJsonResponse(String message) {
            try {
                com.fasterxml.jackson.databind.node.ObjectNode root = objectMapper.createObjectNode();
                com.fasterxml.jackson.databind.node.ArrayNode content = root.putArray("content");
                com.fasterxml.jackson.databind.node.ObjectNode text = content.addObject();
                text.put("type", "text");
                text.put("text", message);
                return objectMapper.writeValueAsString(root);
            } catch (Exception ex) {
                return "{\"content\":[{\"type\":\"text\",\"text\":\"" + message.replace("\"", "\\\"") + "\"}]}";
            }
        }

        private boolean isWriteTool(String toolName) {
            if (!StringUtils.hasText(toolName)) {
                return false;
            }
            String normalized = toolName.toLowerCase(Locale.ROOT);
            return matchesToolSuffix(normalized, "write_file");
        }

        private boolean isReadTool(String toolName) {
            if (!StringUtils.hasText(toolName)) {
                return false;
            }
            String normalized = toolName.toLowerCase(Locale.ROOT);
            return matchesToolSuffix(normalized, "read_file");
        }

        private boolean isListTool(String toolName) {
            if (!StringUtils.hasText(toolName)) {
                return false;
            }
            String normalized = toolName.toLowerCase(Locale.ROOT);
            return matchesToolSuffix(normalized, "list_directory");
        }

        private boolean matchesToolSuffix(String name, String tool) {
            if (tool.equals(name)) {
                return true;
            }
            return name.endsWith("." + tool)
                    || name.endsWith("/" + tool)
                    || name.endsWith(":" + tool);
        }

        private String extractPathFromInput(String input) {
            if (!StringUtils.hasText(input)) {
                return "";
            }
            try {
                com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(input);
                if (node.hasNonNull("path")) {
                    return node.get("path").asText();
                }
                if (node.hasNonNull("file_path")) {
                    return node.get("file_path").asText();
                }
                if (node.hasNonNull("filePath")) {
                    return node.get("filePath").asText();
                }
            } catch (Exception ignore) {
                // ignore parse errors
            }
            return "";
        }

        private String extractPathFromMessage(String message) {
            if (!StringUtils.hasText(message)) {
                return "";
            }
            String[] markers = {"open '", "open \"", "exist: ", "file not found: "};
            for (String marker : markers) {
                int start = message.toLowerCase(Locale.ROOT).indexOf(marker);
                if (start >= 0) {
                    int begin = start + marker.length();
                    int end = message.indexOf('\'', begin);
                    if (end < 0) {
                        end = message.indexOf('"', begin);
                    }
                    if (end < 0) {
                        end = message.length();
                    }
                    return message.substring(begin, end).trim();
                }
            }
            return "";
        }

        private String buildDirectoryHint(String path) {
            try {
                java.nio.file.Path parent = java.nio.file.Paths.get(path).getParent();
                String parentPath = parent == null ? "" : parent.toString();
                FileListing listing = fileService.list(parentPath);
                List<FileEntry> entries = listing.entries();
                if (entries == null || entries.isEmpty()) {
                    return "Directory is empty.";
                }
                String names = entries.stream()
                        .limit(20)
                        .map(FileEntry::name)
                        .toList()
                        .toString();
                return "Directory entries: " + names;
            } catch (Exception ignore) {
                return "";
            }
        }

        private String reflectName(@Nullable Object target) {
            if (target == null) {
                return "";
            }
            for (String method : java.util.List.of("getName", "name", "id")) {
                try {
                    java.lang.reflect.Method m = target.getClass().getMethod(method);
                    Object value = m.invoke(target);
                    if (value instanceof String name && StringUtils.hasText(name)) {
                        return name;
                    }
                } catch (Exception ignore) {
                    // ignore reflection failures
                }
            }
            return "";
        }
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

    private boolean isCancelled(@Nullable String streamId) {
        return streamId != null && streamService.isCancelled(streamId);
    }

    private boolean handleCancellation(@Nullable String streamId, OrchestrationSession session, String statusMessage) {
        if (!isCancelled(streamId)) {
            return false;
        }
        if (streamId != null) {
            streamService.emitStatus(streamId, statusMessage);
            streamService.emitRunComplete(streamId, "CANCELLED");
        }
        persistenceService.completeSession(session, null, "CANCELLED");
        return true;
    }

    private WorkerResult cancelledResult(TaskSpec task, @Nullable String streamId) {
        WorkerResult result = new WorkerResult(task.id(), task.role(), "Cancelled.");
        if (streamId != null) {
            streamService.emitTaskOutput(streamId, result);
            streamService.emitTaskComplete(streamId, result);
        }
        return result;
    }
}
