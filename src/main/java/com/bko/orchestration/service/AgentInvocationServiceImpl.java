package com.bko.orchestration.service;

import static com.bko.orchestration.OrchestrationConstants.*;

import com.bko.config.MultiAgentProperties;
import com.bko.entity.OrchestrationSession;
import com.bko.orchestration.api.AgentInvocationService;
import com.bko.orchestration.api.StatePersistenceService;
import com.bko.orchestration.collaboration.CollaborationStage;
import com.bko.orchestration.collaboration.CollaborationStrategy;
import com.bko.orchestration.model.OrchestratorPlan;
import com.bko.orchestration.model.RoleSelection;
import com.bko.orchestration.model.TaskSpec;
import com.bko.orchestration.model.WorkerResult;
import com.bko.files.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AgentInvocationServiceImpl implements AgentInvocationService {

    private final ChatClient chatClient;
    private final ChatClient openAiChatClient;
    private final MultiAgentProperties properties;
    private final ToolCallbackProvider toolCallbackProvider;
    private final ToolAccessPolicy toolAccessPolicy;
    private final OrchestrationPromptService orchestrationPromptService;
    private final OrchestrationContextService orchestrationContextService;
    private final JsonProcessingService jsonProcessingService;
    private final StatePersistenceService persistenceService;
    private final OrchestrationMetricsService metricsService;
    private final FileService fileService;

    public AgentInvocationServiceImpl(ChatClient chatClient,
                                      @Qualifier("openAiChatClient") ObjectProvider<ChatClient> openAiChatClientProvider,
                                      MultiAgentProperties properties,
                                      ToolCallbackProvider toolCallbackProvider,
                                      ToolAccessPolicy toolAccessPolicy,
                                      OrchestrationPromptService orchestrationPromptService,
                                      OrchestrationContextService orchestrationContextService,
                                      JsonProcessingService jsonProcessingService,
                                      StatePersistenceService persistenceService,
                                      OrchestrationMetricsService metricsService,
                                      FileService fileService) {
        this.chatClient = chatClient;
        this.openAiChatClient = openAiChatClientProvider.getIfAvailable();
        this.properties = properties;
        this.toolCallbackProvider = toolCallbackProvider;
        this.toolAccessPolicy = toolAccessPolicy;
        this.orchestrationPromptService = orchestrationPromptService;
        this.orchestrationContextService = orchestrationContextService;
        this.jsonProcessingService = jsonProcessingService;
        this.persistenceService = persistenceService;
        this.metricsService = metricsService;
        this.fileService = fileService;
    }

    @Override
    public OrchestratorPlan requestPlan(OrchestrationSession session,
                                        String userMessage,
                                        boolean requiresEdits,
                                        List<String> allowedRoles,
                                        @Nullable String context,
                                        String provider,
                                        String model) {
        try {
            String registry = orchestrationContextService.buildRoleRegistry(allowedRoles);
            String systemPrompt = orchestrationPromptService.orchestratorSystemPrompt(requiresEdits, allowedRoles, registry);
            String normalizedContext = orchestrationContextService.defaultContext(context);
            metricsService.recordLlmRequest(PURPOSE_PLAN, null);
            String response = applyTools(getChatRequestSpec(provider, model), ToolAccessPolicy.Phase.ORCHESTRATOR, null)
                    .system(systemPrompt)
                    .user(user -> user.text(ORCHESTRATOR_USER_TEMPLATE)
                            .param("input", userMessage)
                            .param("context", normalizedContext))
                    .call()
                    .content();
            persistenceService.logPrompt(session, PURPOSE_PLAN, null, systemPrompt, ORCHESTRATOR_USER_TEMPLATE,
                    Map.of("input", userMessage, "context", normalizedContext), response);
            OrchestratorPlan plan = jsonProcessingService.parseJsonResponse(PURPOSE_PLAN, response, OrchestratorPlan.class);
            if (plan == null) {
                String retryPrompt = systemPrompt + INVALID_JSON_RETRY_PROMPT;
                metricsService.recordLlmRequest(PURPOSE_PLAN_RETRY, null);
                String retryResponse = applyTools(getChatRequestSpec(provider, model), ToolAccessPolicy.Phase.ORCHESTRATOR, null)
                        .system(retryPrompt)
                        .user(user -> user.text(ORCHESTRATOR_USER_TEMPLATE)
                                .param("input", userMessage)
                                .param("context", normalizedContext))
                        .call()
                        .content();
                persistenceService.logPrompt(session, PURPOSE_PLAN_RETRY, null, retryPrompt, ORCHESTRATOR_USER_TEMPLATE,
                        Map.of("input", userMessage, "context", normalizedContext), retryResponse);
                plan = jsonProcessingService.parseJsonResponse(PURPOSE_PLAN_RETRY, retryResponse, OrchestratorPlan.class);
            }
            metricsService.recordPlanResponse(PURPOSE_PLAN, plan);
            return plan;
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public OrchestratorPlan requestContinuationPlan(OrchestrationSession session,
                                                    String userMessage,
                                                    boolean requiresEdits,
                                                    List<String> allowedRoles,
                                                    @Nullable String context,
                                                    @Nullable String errorSummary,
                                                    OrchestratorPlan plan,
                                                    List<WorkerResult> results,
                                                    String provider,
                                                    String model) {
        try {
            String systemPrompt = orchestrationPromptService.executionReviewPrompt(requiresEdits, allowedRoles);
            String normalizedContext = orchestrationContextService.defaultContext(context);
            String planJson = jsonProcessingService.toJson(plan);
            String resultsJson = jsonProcessingService.toJson(results);
            String normalizedErrors = StringUtils.hasText(errorSummary) ? errorSummary : "None.";
            metricsService.recordLlmRequest(PURPOSE_PLAN_REVIEW, null);
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
            persistenceService.logPrompt(session, PURPOSE_PLAN_REVIEW, null, systemPrompt, EXECUTION_REVIEW_USER_TEMPLATE,
                    Map.of("input", userMessage, "context", normalizedContext, "errors", normalizedErrors,
                            "plan", planJson, "results", resultsJson), response);
            OrchestratorPlan continuation = jsonProcessingService.parseJsonResponse(PURPOSE_PLAN_REVIEW, response, OrchestratorPlan.class);
            if (continuation == null) {
                String retryPrompt = systemPrompt + INVALID_JSON_RETRY_PROMPT;
                metricsService.recordLlmRequest(PURPOSE_PLAN_REVIEW_RETRY, null);
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
                persistenceService.logPrompt(session, PURPOSE_PLAN_REVIEW_RETRY, null, retryPrompt, EXECUTION_REVIEW_USER_TEMPLATE,
                        Map.of("input", userMessage, "context", normalizedContext, "errors", normalizedErrors,
                                "plan", planJson, "results", resultsJson), retryResponse);
                continuation = jsonProcessingService.parseJsonResponse(PURPOSE_PLAN_REVIEW_RETRY, retryResponse, OrchestratorPlan.class);
            }
            metricsService.recordPlanResponse(PURPOSE_PLAN_REVIEW, continuation);
            return continuation;
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public RoleSelection requestRoleSelection(OrchestrationSession session,
                                              String userMessage,
                                              boolean requiresEdits,
                                              List<String> availableRoles,
                                              @Nullable String context,
                                              String provider,
                                              String model) {
        String registry = orchestrationContextService.buildRoleRegistry(availableRoles);
        try {
            String systemPrompt = orchestrationPromptService.roleSelectionPrompt(requiresEdits, availableRoles);
            String normalizedContext = orchestrationContextService.defaultContext(context);
            metricsService.recordLlmRequest(PURPOSE_ROLE_SELECTION, null);
            String response = applyTools(getChatRequestSpec(provider, model), ToolAccessPolicy.Phase.ORCHESTRATOR, null)
                    .system(systemPrompt)
                    .user(user -> user.text(ROLE_SELECTION_USER_TEMPLATE)
                            .param("input", userMessage)
                            .param("context", normalizedContext)
                            .param("roles", registry))
                    .call()
                    .content();
            persistenceService.logPrompt(session, PURPOSE_ROLE_SELECTION, null, systemPrompt, ROLE_SELECTION_USER_TEMPLATE,
                    Map.of("input", userMessage, "context", normalizedContext, "roles", registry), response);
            RoleSelection selection = jsonProcessingService.parseJsonResponse(PURPOSE_ROLE_SELECTION, response, RoleSelection.class);
            if (selection == null) {
                String retryPrompt = orchestrationPromptService.roleSelectionPrompt(requiresEdits, availableRoles)
                        + INVALID_JSON_RETRY_PROMPT;
                metricsService.recordLlmRequest(PURPOSE_ROLE_SELECTION_RETRY, null);
                String retryResponse = applyTools(getChatRequestSpec(provider, model), ToolAccessPolicy.Phase.ORCHESTRATOR, null)
                        .system(retryPrompt)
                        .user(user -> user.text(ROLE_SELECTION_USER_TEMPLATE)
                                .param("input", userMessage)
                                .param("context", normalizedContext)
                                .param("roles", registry))
                        .call()
                        .content();
                persistenceService.logPrompt(session, PURPOSE_ROLE_SELECTION_RETRY, null, retryPrompt, ROLE_SELECTION_USER_TEMPLATE,
                        Map.of("input", userMessage, "context", normalizedContext, "roles", registry), retryResponse);
                selection = jsonProcessingService.parseJsonResponse(PURPOSE_ROLE_SELECTION_RETRY, retryResponse, RoleSelection.class);
            }
            return selection;
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public WorkerCallResult runWorkerPrompt(OrchestrationSession session,
                                            String systemPrompt,
                                            String userMessage,
                                            TaskSpec task,
                                            String normalizedContext,
                                            String provider,
                                            String model,
                                            ToolAccessPolicy.Phase phase) {
        ToolCallAudit audit = new ToolCallAudit(task.role(), task.id());
        metricsService.recordLlmRequest(PURPOSE_WORKER_TASK, task.role());
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

    @Override
    public String collaborateRound(OrchestrationSession session,
                                   String userMessage,
                                   TaskSpec task,
                                   int round,
                                   CollaborationStage stage,
                                   CollaborationStrategy strategy,
                                   boolean finalStage,
                                   List<WorkerResult> results,
                                   String provider,
                                   String model) {
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

    @Override
    public String synthesize(OrchestrationSession session,
                             String userMessage,
                             OrchestratorPlan plan,
                             List<WorkerResult> results,
                             String provider,
                             String model) {
        String planJson = jsonProcessingService.toJson(plan);
        String resultsJson = jsonProcessingService.toJson(results);
        metricsService.recordLlmRequest(PURPOSE_SYTHESIS, null);
        String systemPrompt = orchestrationPromptService.synthesisSystemPrompt();
        String out = applyTools(getChatRequestSpec(provider, model), ToolAccessPolicy.Phase.SYNTHESIS, null)
                .system(systemPrompt)
                .user(user -> user.text(SYNTHESIS_USER_TEMPLATE)
                        .param("input", userMessage)
                        .param("plan", planJson)
                        .param("results", resultsJson))
                .call()
                .content();
        persistenceService.logPrompt(session, PURPOSE_SYTHESIS, null, systemPrompt, SYNTHESIS_USER_TEMPLATE,
                Map.of("input", userMessage, "plan", planJson, "results", resultsJson), out);
        return out;
    }

    private ChatClient.ChatClientRequestSpec getChatRequestSpec(String provider, String model) {
        String activeProvider = StringUtils.hasText(provider) ? provider.toUpperCase() : properties.getAiProvider().name();
        String activeModel = StringUtils.hasText(model) ? model : properties.getOpenai().getModel();

        if ("OPENAI".equals(activeProvider)) {
            if (openAiChatClient == null) {
                throw new IllegalStateException("OpenAI provider is not properly configured. "
                        + "Check that you have a valid API key or a custom Base URL in your configuration.");
            }
            var spec = openAiChatClient.prompt();
            if (StringUtils.hasText(activeModel)) {
                spec = spec.options(OpenAiChatOptions.builder().model(activeModel).build());
            }
            return spec;
        }
        return chatClient.prompt();
    }

    private ChatClient.ChatClientRequestSpec applyTools(ChatClient.ChatClientRequestSpec prompt,
                                                        ToolAccessPolicy.Phase phase,
                                                        @Nullable String role) {
        return applyTools(prompt, phase, role, null);
    }

    private ChatClient.ChatClientRequestSpec applyTools(ChatClient.ChatClientRequestSpec prompt,
                                                        ToolAccessPolicy.Phase phase,
                                                        @Nullable String role,
                                                        @Nullable ToolCallAudit audit) {
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
}
