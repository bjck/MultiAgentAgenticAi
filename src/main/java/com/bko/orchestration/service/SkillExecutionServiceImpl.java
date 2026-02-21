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
    private final StatePersistenceService persistenceService;
    private final EventProcessingService eventProcessingService;
    private final ExecutorService workerExecutor;

    public SkillExecutionServiceImpl(MultiAgentProperties properties,
                                     OrchestrationPromptService orchestrationPromptService,
                                     OrchestrationContextService orchestrationContextService,
                                     CollaborationStrategyService collaborationStrategyService,
                                     AgentInvocationService agentInvocationService,
                                     StatePersistenceService persistenceService,
                                     EventProcessingService eventProcessingService,
                                     @org.springframework.beans.factory.annotation.Qualifier("workerExecutor") ExecutorService workerExecutor) {
        this.properties = properties;
        this.orchestrationPromptService = orchestrationPromptService;
        this.orchestrationContextService = orchestrationContextService;
        this.collaborationStrategyService = collaborationStrategyService;
        this.agentInvocationService = agentInvocationService;
        this.persistenceService = persistenceService;
        this.eventProcessingService = eventProcessingService;
        this.workerExecutor = workerExecutor;
    }

    @Override
    public WorkerResult runWorker(OrchestrationSession session,
                                  String userMessage,
                                  TaskSpec task,
                                  boolean requiresEdits,
                                  @Nullable String context,
                                  String provider,
                                  String model,
                                  boolean includeHandoffSchema,
                                  boolean requireToolCalls,
                                  @Nullable TaskLog taskLog,
                                  @Nullable String streamId) {
        if (eventProcessingService.isCancelled(streamId)) {
            return cancelledResult(task, streamId);
        }
        String normalizedContext = orchestrationContextService.defaultContext(context);
        String systemPrompt = orchestrationPromptService.workerSystemPrompt(task.role(), requiresEdits, includeHandoffSchema);
        WorkerCallResult callResult = agentInvocationService.runWorkerPrompt(session, systemPrompt, userMessage, task, normalizedContext,
                provider, model, ToolAccessPolicy.Phase.WORKER);
        logToolCalls(session, taskLog, task, callResult.audit());
        if (requireToolCalls && callResult.toolCallCount() == 0) {
            int attempts = 1;
            while (attempts < MAX_TOOL_CALL_ATTEMPTS && callResult.toolCallCount() == 0) {
                String retryPrompt = systemPrompt + "\n\n" +
                        "Your last response did not call any MCP filesystem tools. Tool calls are required for this task.\n" +
                        "Use list_directory/read_file to inspect the repository, then return your findings.\n";
                callResult = agentInvocationService.runWorkerPrompt(session, retryPrompt, userMessage, task, normalizedContext,
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
                String retryPrompt = systemPrompt + "\n\n" +
                        "Your last response did not call any MCP filesystem tools. Tool calls are mandatory for this task.\n" +
                        "Use MCP tools to read and write files, then respond with a concise summary of changes and next steps.\n";
                callResult = agentInvocationService.runWorkerPrompt(session, retryPrompt, userMessage, task, normalizedContext,
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
                String retryPrompt = systemPrompt + "\n\n" +
                        "Your last response did not call the write_file tool. File edits are mandatory for implementer tasks.\n" +
                        "You must call write_file to apply the changes, then respond with a concise summary and next steps.\n";
                callResult = agentInvocationService.runWorkerPrompt(session, retryPrompt, userMessage, task, normalizedContext,
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
        eventProcessingService.emitTaskOutput(streamId, result);
        eventProcessingService.emitTaskComplete(streamId, result);
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

    @Override
    public WorkerResult runCollaborativeTask(OrchestrationSession session,
                                             String userMessage,
                                             TaskSpec task,
                                             boolean requiresEdits,
                                             @Nullable String baseContext,
                                             String provider,
                                             String model,
                                             @Nullable TaskLog taskLog,
                                             @Nullable String streamId) {
        if (eventProcessingService.isCancelled(streamId)) {
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
            if (eventProcessingService.isCancelled(streamId)) {
                return cancelledResult(task, streamId);
            }
            for (int stageIndex = 0; stageIndex < stages.size(); stageIndex++) {
                if (eventProcessingService.isCancelled(streamId)) {
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
                            eventProcessingService.emitTaskStart(streamId, subTask);
                            return CompletableFuture.supplyAsync(
                                            () -> runWorker(session, userMessage, subTask, stageRequiresEdits, roundContext, provider, model,
                                                    false, false, taskLog, streamId),
                                            workerExecutor)
                                    .orTimeout(properties.getWorkerTimeout().toSeconds(), TimeUnit.SECONDS)
                                    .exceptionally(ex -> {
                                        WorkerResult failed = new WorkerResult(subTask.id(), subTask.role(),
                                                WORKER_FAILED_MESSAGE + ex.getMessage());
                                        eventProcessingService.emitTaskOutput(streamId, failed);
                                        eventProcessingService.emitTaskComplete(streamId, failed);
                                        return failed;
                                    });
                        })
                        .toList();
                if (eventProcessingService.isCancelled(streamId)) {
                    futures.forEach(future -> future.cancel(true));
                    return cancelledResult(task, streamId);
                }
                List<WorkerResult> roundResults = futures.stream().map(CompletableFuture::join).toList();
                String summary = agentInvocationService.collaborateRound(session, userMessage, task, round, stage, strategy,
                        finalStage, roundResults, provider, model);
                finalSummary = summary;
                rollingContext = orchestrationContextService.mergeContexts(rollingContext, summary);
                TaskSpec summaryTask = new TaskSpec(task.id() + "-r" + round + "-" + stage.key() + "-summary", task.role(),
                        "Collaboration summary for round " + round + " (" + stage.label() + ")", "Summarize best findings.");
                WorkerResult summaryResult = new WorkerResult(summaryTask.id(), summaryTask.role(), summary);
                eventProcessingService.emitTaskStart(streamId, summaryTask);
                eventProcessingService.emitTaskOutput(streamId, summaryResult);
                eventProcessingService.emitTaskComplete(streamId, summaryResult);
            }
        }
        WorkerResult finalResult = new WorkerResult(task.id(), task.role(), finalSummary);
        eventProcessingService.emitTaskOutput(streamId, finalResult);
        eventProcessingService.emitTaskComplete(streamId, finalResult);
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

    private WorkerResult cancelledResult(TaskSpec task, @Nullable String streamId) {
        WorkerResult result = new WorkerResult(task.id(), task.role(), "Cancelled.");
        eventProcessingService.emitTaskOutput(streamId, result);
        eventProcessingService.emitTaskComplete(streamId, result);
        return result;
    }
}
