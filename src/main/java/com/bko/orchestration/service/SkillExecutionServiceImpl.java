package com.bko.orchestration.service;

import static com.bko.orchestration.OrchestrationConstants.*;

import com.bko.config.MultiAgentProperties;
import com.bko.entity.OrchestrationSession;
import com.bko.entity.TaskLog;
import com.bko.orchestration.api.AgentInvocationService;
import com.bko.orchestration.api.EventProcessingService;
import com.bko.orchestration.api.SkillExecutionService;
import com.bko.orchestration.api.StatePersistenceService;
import com.bko.orchestration.collaboration.CollaborationStage;
import com.bko.orchestration.collaboration.CollaborationStrategy;
import com.bko.orchestration.collaboration.CollaborationStrategyService;
import com.bko.orchestration.model.TaskSpec;
import com.bko.orchestration.model.WorkerResult;
import com.bko.orchestration.service.SkillPlanningService.SkillPlanningResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class SkillExecutionServiceImpl implements SkillExecutionService {

    private static final int MAX_TOOL_CALL_ATTEMPTS = 2;

    private final MultiAgentProperties properties;
    private final OrchestrationPromptService orchestrationPromptService;
    private final OrchestrationContextService orchestrationContextService;
    private final CollaborationStrategyService collaborationStrategyService;
    private final AgentInvocationService agentInvocationService;
    private final SkillPlanningService skillPlanningService;
    private final StatePersistenceService persistenceService;
    private final EventProcessingService eventProcessingService;
    private final ExecutorService workerExecutor;

    public SkillExecutionServiceImpl(MultiAgentProperties properties,
                                     OrchestrationPromptService orchestrationPromptService,
                                     OrchestrationContextService orchestrationContextService,
                                     CollaborationStrategyService collaborationStrategyService,
                                     AgentInvocationService agentInvocationService,
                                     SkillPlanningService skillPlanningService,
                                     StatePersistenceService persistenceService,
                                     EventProcessingService eventProcessingService,
                                     @org.springframework.beans.factory.annotation.Qualifier("workerExecutor") ExecutorService workerExecutor) {
        this.properties = properties;
        this.orchestrationPromptService = orchestrationPromptService;
        this.orchestrationContextService = orchestrationContextService;
        this.collaborationStrategyService = collaborationStrategyService;
        this.agentInvocationService = agentInvocationService;
        this.skillPlanningService = skillPlanningService;
        this.persistenceService = persistenceService;
        this.eventProcessingService = eventProcessingService;
        this.workerExecutor = workerExecutor;
    }

    @Override
    public WorkerResult runWorker(OrchestrationSession session,
                                  String userMessage,
                                  TaskSpec task,
                                  @Nullable String context,
                                  String provider,
                                  String model,
                                  boolean includeHandoffSchema,
                                  boolean requireToolCalls,
                                  @Nullable TaskLog taskLog,
                                  @Nullable String streamId,
                                  @Nullable List<com.bko.config.AgentSkill> selectedSkills) {
        if (eventProcessingService.isCancelled(streamId)) {
            return cancelledResult(task, streamId);
        }
        String normalizedContext = orchestrationContextService.defaultContext(context);
        List<com.bko.config.AgentSkill> effectiveSkills = selectedSkills;
        if (effectiveSkills == null) {
            SkillPlanningResult plan = skillPlanningService.planForTask(session, userMessage, task, normalizedContext, provider, model);
            effectiveSkills = plan.selectedSkills();
        }
        String systemPrompt = orchestrationPromptService.workerSystemPrompt(task.role(), includeHandoffSchema, effectiveSkills);
        WorkerCallResult callResult = agentInvocationService.runWorkerPrompt(session, systemPrompt, userMessage, task, normalizedContext,
                provider, model, ToolAccessPolicy.Phase.WORKER);
        logToolCalls(session, taskLog, task, callResult.audit());
        if (requireToolCalls && callResult.toolCallCount() == 0) {
            int attempts = 1;
            while (attempts < MAX_TOOL_CALL_ATTEMPTS && callResult.toolCallCount() == 0) {
                String retryPrompt = systemPrompt + "\n\n" +
                        "Your last response did not call any tools. Tool calls are required for this task.\n" +
                        "Use the available tools to gather needed information, then return your findings.\n";
                callResult = agentInvocationService.runWorkerPrompt(session, retryPrompt, userMessage, task, normalizedContext,
                        provider, model, ToolAccessPolicy.Phase.WORKER);
                logToolCalls(session, taskLog, task, callResult.audit());
                attempts++;
            }
            if (callResult.toolCallCount() == 0) {
                log.warn("Task {} returned without tool calls after {} attempts.", task.id(), MAX_TOOL_CALL_ATTEMPTS);
            }
        }
        String output = callResult.output();
        WorkerResult result = new WorkerResult(task.id(), task.role(), output);
        eventProcessingService.emitTaskOutput(streamId, result);
        eventProcessingService.emitTaskComplete(streamId, result);
        try {
            Map<String, String> params = Map.of(
                    "input", userMessage,
                    "context", normalizedContext,
                    "task", task.description(),
                    "expectedOutput", task.expectedOutput()
            );
            persistenceService.logPrompt(session, PURPOSE_WORKER_TASK, task.role(), systemPrompt, WORKER_USER_TEMPLATE, params, output,
                    callResult.inputTokens(), callResult.outputTokens());
            persistenceService.logWorkerResult(session, taskLog, task.role(), output);
        } catch (Exception ex) {
            log.warn("Failed to persist worker execution logs. sessionId={}, taskId={}, role={}",
                    session != null ? session.getId() : null,
                    task != null ? task.id() : null,
                    task != null ? task.role() : null,
                    ex);
        }
        return result;
    }

    @Override
    public WorkerResult runCollaborativeTask(OrchestrationSession session,
                                             String userMessage,
                                             TaskSpec task,
                                             @Nullable String baseContext,
                                             String provider,
                                             String model,
                                             @Nullable TaskLog taskLog,
                                             @Nullable String streamId) {
        // In the simplified single-agent model, collaborative execution is not used.
        // Delegate directly to the standard worker execution path.
        return runWorker(session, userMessage, task, baseContext, provider, model,
                false, false, taskLog, streamId, null);
    }

    private void logToolCalls(OrchestrationSession session, @Nullable TaskLog taskLog, TaskSpec task, @Nullable ToolCallAudit audit) {
        if (audit == null) {
            return;
        }
        for (ToolCallRecord record : audit.snapshot()) {
            try {
                persistenceService.logToolCall(session, taskLog, task.role(), record.name(), record.input(), record.output());
            } catch (Exception ex) {
                log.warn("Failed to persist tool call audit. sessionId={}, taskId={}, role={}, tool={}",
                        session != null ? session.getId() : null,
                        task != null ? task.id() : null,
                        task != null ? task.role() : null,
                        record != null ? record.name() : null,
                        ex);
            }
        }
    }

    private WorkerResult cancelledResult(TaskSpec task, @Nullable String streamId) {
        WorkerResult result = new WorkerResult(task.id(), task.role(), "Cancelled.");
        eventProcessingService.emitTaskOutput(streamId, result);
        eventProcessingService.emitTaskComplete(streamId, result);
        return result;
    }
}
