package com.bko.orchestration.service;

import static com.bko.orchestration.OrchestrationConstants.*;

import com.bko.config.MultiAgentProperties;
import com.bko.entity.OrchestrationSession;
import com.bko.orchestration.api.AgentInvocationService;
import com.bko.orchestration.api.StatePersistenceService;
import com.bko.orchestration.model.OrchestratorPlan;
import com.bko.orchestration.model.SkillSelection;
import com.bko.orchestration.model.SkillSummary;
import com.bko.orchestration.model.TaskSpec;
import com.bko.orchestration.model.WorkerResult;
import com.bko.files.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
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

    private final GoogleGenAiChatModel googleGenAiChatModel;
    private final ObjectProvider<OpenAiChatModel> openAiChatModelProvider;
    private final MultiAgentProperties properties;
    private final ToolCallbackProvider toolCallbackProvider;
    private final ToolAccessPolicy toolAccessPolicy;
    private final OrchestrationPromptService orchestrationPromptService;
    private final OrchestrationContextService orchestrationContextService;
    private final JsonProcessingService jsonProcessingService;
    private final StatePersistenceService persistenceService;
    private final OrchestrationMetricsService metricsService;
    private final FileService fileService;

    public AgentInvocationServiceImpl(GoogleGenAiChatModel googleGenAiChatModel,
                                      ObjectProvider<OpenAiChatModel> openAiChatModelProvider,
                                      MultiAgentProperties properties,
                                      ToolCallbackProvider toolCallbackProvider,
                                      ToolAccessPolicy toolAccessPolicy,
                                      OrchestrationPromptService orchestrationPromptService,
                                      OrchestrationContextService orchestrationContextService,
                                      JsonProcessingService jsonProcessingService,
                                      StatePersistenceService persistenceService,
                                      OrchestrationMetricsService metricsService,
                                      FileService fileService) {
        this.googleGenAiChatModel = googleGenAiChatModel;
        this.openAiChatModelProvider = openAiChatModelProvider;
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
                                        List<String> allowedRoles,
                                        @Nullable String context,
                                        String provider,
                                        String model) {
        try {
            String registry = orchestrationContextService.buildRoleRegistry(allowedRoles);
            String systemPrompt = orchestrationPromptService.orchestratorSystemPrompt(allowedRoles, registry);
            String normalizedContext = orchestrationContextService.defaultContext(context);
            metricsService.recordLlmRequest(PURPOSE_PLAN, null);
            // Plan without toolCallbacks to avoid Spring AI building an empty advisor chain (No CallAdvisors)
            var callSpec = getChatRequestSpec(provider, model)
                    .system(systemPrompt)
                    .user(user -> user.text(ORCHESTRATOR_USER_TEMPLATE)
                            .param("input", userMessage)
                            .param("context", normalizedContext))
                    .call();
            ChatResponse chatResponse = callSpec.chatResponse();
            String response = extractContent(chatResponse);
            var usage = extractUsage(chatResponse);
            persistenceService.logPrompt(session, PURPOSE_PLAN, null, systemPrompt, ORCHESTRATOR_USER_TEMPLATE,
                    Map.of("input", userMessage, "context", normalizedContext), response, usage[0], usage[1]);
            OrchestratorPlan plan = jsonProcessingService.parseJsonResponse(PURPOSE_PLAN, response, OrchestratorPlan.class);
            if (plan == null) {
                String retryPrompt = systemPrompt + INVALID_JSON_RETRY_PROMPT;
                metricsService.recordLlmRequest(PURPOSE_PLAN_RETRY, null);
                var retryCallSpec = getChatRequestSpec(provider, model)
                        .system(retryPrompt)
                        .user(user -> user.text(ORCHESTRATOR_USER_TEMPLATE)
                                .param("input", userMessage)
                                .param("context", normalizedContext))
                        .call();
                ChatResponse retryChatResponse = retryCallSpec.chatResponse();
                String retryResponse = extractContent(retryChatResponse);
                var retryUsage = extractUsage(retryChatResponse);
                persistenceService.logPrompt(session, PURPOSE_PLAN_RETRY, null, retryPrompt, ORCHESTRATOR_USER_TEMPLATE,
                        Map.of("input", userMessage, "context", normalizedContext), retryResponse, retryUsage[0], retryUsage[1]);
                plan = jsonProcessingService.parseJsonResponse(PURPOSE_PLAN_RETRY, retryResponse, OrchestratorPlan.class);
            }
            metricsService.recordPlanResponse(PURPOSE_PLAN, plan);
            return plan;
        } catch (Exception ex) {
            log.error("Failed to request orchestrator plan. sessionId={}, provider={}, model={}",
                    session != null ? session.getId() : null, provider, model, ex);
            return null;
        }
    }

    @Override
    public OrchestratorPlan requestContinuationPlan(OrchestrationSession session,
                                                    String userMessage,
                                                    List<String> allowedRoles,
                                                    @Nullable String context,
                                                    @Nullable String errorSummary,
                                                    OrchestratorPlan plan,
                                                    List<WorkerResult> results,
                                                    String provider,
                                                    String model) {
        try {
            String systemPrompt = orchestrationPromptService.executionReviewPrompt(allowedRoles);
            String normalizedContext = orchestrationContextService.defaultContext(context);
            String planJson = jsonProcessingService.toJson(plan);
            String resultsJson = jsonProcessingService.toJson(results);
            String normalizedErrors = StringUtils.hasText(errorSummary) ? errorSummary : "None.";
            metricsService.recordLlmRequest(PURPOSE_PLAN_REVIEW, null);
            var callSpec = applyTools(getChatRequestSpec(provider, model), ToolAccessPolicy.Phase.ORCHESTRATOR, null)
                    .system(systemPrompt)
                    .user(user -> user.text(EXECUTION_REVIEW_USER_TEMPLATE)
                            .param("input", userMessage)
                            .param("context", normalizedContext)
                            .param("errors", normalizedErrors)
                            .param("plan", planJson)
                            .param("results", resultsJson))
                    .call();
            ChatResponse chatResponse = callSpec.chatResponse();
            String response = extractContent(chatResponse);
            var usage = extractUsage(chatResponse);
            persistenceService.logPrompt(session, PURPOSE_PLAN_REVIEW, null, systemPrompt, EXECUTION_REVIEW_USER_TEMPLATE,
                    Map.of("input", userMessage, "context", normalizedContext, "errors", normalizedErrors,
                            "plan", planJson, "results", resultsJson), response, usage[0], usage[1]);
            OrchestratorPlan continuation = jsonProcessingService.parseJsonResponse(PURPOSE_PLAN_REVIEW, response, OrchestratorPlan.class);
            if (continuation == null) {
                String retryPrompt = systemPrompt + INVALID_JSON_RETRY_PROMPT;
                metricsService.recordLlmRequest(PURPOSE_PLAN_REVIEW_RETRY, null);
                var retryCallSpec = applyTools(getChatRequestSpec(provider, model), ToolAccessPolicy.Phase.ORCHESTRATOR, null)
                        .system(retryPrompt)
                        .user(user -> user.text(EXECUTION_REVIEW_USER_TEMPLATE)
                                .param("input", userMessage)
                                .param("context", normalizedContext)
                                .param("errors", normalizedErrors)
                                .param("plan", planJson)
                                .param("results", resultsJson))
                        .call();
                ChatResponse retryChatResponse = retryCallSpec.chatResponse();
                String retryResponse = extractContent(retryChatResponse);
                var retryUsage = extractUsage(retryChatResponse);
                persistenceService.logPrompt(session, PURPOSE_PLAN_REVIEW_RETRY, null, retryPrompt, EXECUTION_REVIEW_USER_TEMPLATE,
                        Map.of("input", userMessage, "context", normalizedContext, "errors", normalizedErrors,
                                "plan", planJson, "results", resultsJson), retryResponse, retryUsage[0], retryUsage[1]);
                continuation = jsonProcessingService.parseJsonResponse(PURPOSE_PLAN_REVIEW_RETRY, retryResponse, OrchestratorPlan.class);
            }
            metricsService.recordPlanResponse(PURPOSE_PLAN_REVIEW, continuation);
            return continuation;
        } catch (Exception ex) {
            log.error("Failed to request continuation plan. sessionId={}, provider={}, model={}",
                    session != null ? session.getId() : null, provider, model, ex);
            return null;
        }
    }

    @Override
    public SkillSelection requestSkillSelection(OrchestrationSession session,
                                                String userMessage,
                                                TaskSpec task,
                                                List<SkillSummary> skills,
                                                int budget,
                                                @Nullable String context,
                                                String provider,
                                                String model) {
        String skillsList = renderSkillList(skills);
        String systemPrompt = SKILL_PLANNER_SYSTEM_PROMPT.formatted(budget);
        String normalizedContext = orchestrationContextService.defaultContext(context);
        try {
            metricsService.recordLlmRequest(PURPOSE_SKILL_PLAN, task != null ? task.role() : null);
            var callSpec = applyTools(getChatRequestSpec(provider, model), ToolAccessPolicy.Phase.ORCHESTRATOR, null)
                    .system(systemPrompt)
                    .user(user -> user.text(SKILL_PLANNER_USER_TEMPLATE)
                            .param("input", userMessage)
                            .param("task", task != null ? task.description() : "")
                            .param("expectedOutput", task != null ? task.expectedOutput() : "")
                            .param("budget", String.valueOf(budget))
                            .param("skills", skillsList))
                    .call();
            ChatResponse chatResponse = callSpec.chatResponse();
            String response = extractContent(chatResponse);
            var usage = extractUsage(chatResponse);
            persistenceService.logPrompt(session, PURPOSE_SKILL_PLAN, task != null ? task.role() : null, systemPrompt,
                    SKILL_PLANNER_USER_TEMPLATE,
                    Map.of("input", userMessage,
                            "task", task != null ? task.description() : "",
                            "expectedOutput", task != null ? task.expectedOutput() : "",
                            "budget", String.valueOf(budget),
                            "skills", skillsList,
                            "context", normalizedContext),
                    response, usage[0], usage[1]);
            SkillSelection selection = jsonProcessingService.parseJsonResponse(PURPOSE_SKILL_PLAN, response, SkillSelection.class);
            if (selection == null) {
                String retryPrompt = systemPrompt + INVALID_JSON_RETRY_PROMPT;
                metricsService.recordLlmRequest(PURPOSE_SKILL_PLAN_RETRY, task != null ? task.role() : null);
                var retryCallSpec = applyTools(getChatRequestSpec(provider, model), ToolAccessPolicy.Phase.ORCHESTRATOR, null)
                        .system(retryPrompt)
                        .user(user -> user.text(SKILL_PLANNER_USER_TEMPLATE)
                                .param("input", userMessage)
                                .param("task", task != null ? task.description() : "")
                                .param("expectedOutput", task != null ? task.expectedOutput() : "")
                                .param("budget", String.valueOf(budget))
                                .param("skills", skillsList))
                        .call();
                ChatResponse retryChatResponse = retryCallSpec.chatResponse();
                String retryResponse = extractContent(retryChatResponse);
                var retryUsage = extractUsage(retryChatResponse);
                persistenceService.logPrompt(session, PURPOSE_SKILL_PLAN_RETRY, task != null ? task.role() : null, retryPrompt,
                        SKILL_PLANNER_USER_TEMPLATE,
                        Map.of("input", userMessage,
                                "task", task != null ? task.description() : "",
                                "expectedOutput", task != null ? task.expectedOutput() : "",
                                "budget", String.valueOf(budget),
                                "skills", skillsList,
                                "context", normalizedContext),
                        retryResponse, retryUsage[0], retryUsage[1]);
                selection = jsonProcessingService.parseJsonResponse(PURPOSE_SKILL_PLAN_RETRY, retryResponse, SkillSelection.class);
            }
            return selection;
        } catch (Exception ex) {
            log.error("Failed to request skill selection. sessionId={}, taskId={}, role={}, provider={}, model={}",
                    session != null ? session.getId() : null,
                    task != null ? task.id() : null,
                    task != null ? task.role() : null,
                    provider,
                    model,
                    ex);
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
        var callSpec = applyTools(getChatRequestSpec(provider, model), phase, task.role(), audit)
                .system(systemPrompt)
                .user(user -> user.text(WORKER_USER_TEMPLATE)
                        .param("input", userMessage)
                        .param("context", normalizedContext)
                        .param("task", task.description())
                        .param("expectedOutput", task.expectedOutput()))
                .call();
        ChatResponse chatResponse = callSpec.chatResponse();
        String output = extractContent(chatResponse);
        var usage = extractUsage(chatResponse);
        return new WorkerCallResult(output == null ? "" : output, audit, usage[0], usage[1]);
    }

    private static String extractContent(@Nullable ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
            return "";
        }
        String text = chatResponse.getResult().getOutput().getText();
        return text == null ? "" : text;
    }

    /**
     * Extracts [inputTokens, outputTokens] from the chat response metadata when available.
     * Returns [null, null] if usage is not present.
     */
    private static Integer[] extractUsage(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getMetadata() == null) {
            return new Integer[]{null, null};
        }
        Usage usage = chatResponse.getMetadata().getUsage();
        if (usage == null) {
            return new Integer[]{null, null};
        }
        return new Integer[]{
                usage.getPromptTokens() != null ? usage.getPromptTokens() : null,
                usage.getCompletionTokens() != null ? usage.getCompletionTokens() : null
        };
    }

    /**
     * Build a fresh ChatClient for this request. Callers must consume the response only once
     * (e.g. chatResponse()) to avoid exhausting the advisor chain.
     */
    private ChatClient.ChatClientRequestSpec getChatRequestSpec(String provider, String model) {
        String activeProvider = StringUtils.hasText(provider) ? provider.toUpperCase() : properties.getAiProvider().name();
        String activeModel = StringUtils.hasText(model) ? model : properties.getOpenai().getModel();

        ChatModel chatModel;
        if ("OPENAI".equals(activeProvider)) {
            OpenAiChatModel openAi = openAiChatModelProvider.getIfAvailable();
            if (openAi == null) {
                throw new IllegalStateException("OpenAI provider is not properly configured. "
                        + "Check that you have a valid API key or a custom Base URL in your configuration.");
            }
            chatModel = openAi;
        } else {
            chatModel = googleGenAiChatModel;
        }
        ChatClient client = ChatClient.builder(chatModel).build();
        var spec = client.prompt();
        if ("OPENAI".equals(activeProvider) && StringUtils.hasText(activeModel)) {
            spec = spec.options(OpenAiChatOptions.builder().model(activeModel).build());
        }
        return spec;
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
        var allowed = toolAccessPolicy.allowedToolNames();
        ToolCallbackProvider filtered = allowed.isEmpty()
                ? toolCallbackProvider
                : new FilteringToolCallbackProvider(toolCallbackProvider, allowed);
        ToolCallbackProvider effective = audit == null ? filtered : auditedToolCallbackProvider(filtered, audit);
        ToolCallback[] callbacks = effective.getToolCallbacks();
        if (callbacks == null || callbacks.length == 0) {
            if (!allowed.isEmpty()) {
                log.warn("No tool callbacks available after filtering. phase={}, role={}, allowed={}, available={}",
                        phase, role, allowed, describeToolNames(rawCallbacks));
            }
            return prompt;
        }
        if (log.isDebugEnabled()) {
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

    private String renderSkillList(List<SkillSummary> skills) {
        if (skills == null || skills.isEmpty()) {
            return "None.";
        }
        StringBuilder sb = new StringBuilder();
        for (SkillSummary skill : skills) {
            if (skill == null || !StringUtils.hasText(skill.name())) {
                continue;
            }
            sb.append("- ").append(skill.name());
            if (StringUtils.hasText(skill.description())) {
                sb.append(": ").append(skill.description().trim());
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }
}
