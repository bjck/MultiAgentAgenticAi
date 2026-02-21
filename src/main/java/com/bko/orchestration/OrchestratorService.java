package com.bko.orchestration;

import static com.bko.orchestration.OrchestrationConstants.*;

import com.bko.entity.OrchestrationSession;
import com.bko.entity.OrchestratorPlanLog;
import com.bko.entity.TaskLog;
import com.bko.orchestration.api.AgentInvocationService;
import com.bko.orchestration.api.EventProcessingService;
import com.bko.orchestration.api.SkillExecutionService;
import com.bko.orchestration.api.StatePersistenceService;
import com.bko.orchestration.api.TaskManagementService;
import com.bko.orchestration.model.AdvisoryBundle;
import com.bko.orchestration.model.DiscoveryBundle;
import com.bko.orchestration.model.FailureDetail;
import com.bko.orchestration.model.OrchestrationResult;
import com.bko.orchestration.model.OrchestratorPlan;
import com.bko.orchestration.model.PlanDraft;
import com.bko.orchestration.model.TaskSpec;
import com.bko.orchestration.model.WorkerResult;
import com.bko.orchestration.service.FileEditDetectionService;
import com.bko.orchestration.service.OrchestrationContextService;
import com.bko.orchestration.service.OrchestrationMetricsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class OrchestratorService {

    private final FileEditDetectionService fileEditDetectionService;
    private final OrchestrationContextService orchestrationContextService;
    private final AgentInvocationService agentInvocationService;
    private final TaskManagementService taskManagementService;
    private final SkillExecutionService skillExecutionService;
    private final StatePersistenceService persistenceService;
    private final EventProcessingService eventProcessingService;
    private final OrchestrationMetricsService metricsService;

    public OrchestratorService(FileEditDetectionService fileEditDetectionService,
                               OrchestrationContextService orchestrationContextService,
                               AgentInvocationService agentInvocationService,
                               TaskManagementService taskManagementService,
                               SkillExecutionService skillExecutionService,
                               StatePersistenceService persistenceService,
                               EventProcessingService eventProcessingService,
                               OrchestrationMetricsService metricsService) {
        this.fileEditDetectionService = fileEditDetectionService;
        this.orchestrationContextService = orchestrationContextService;
        this.agentInvocationService = agentInvocationService;
        this.taskManagementService = taskManagementService;
        this.skillExecutionService = skillExecutionService;
        this.persistenceService = persistenceService;
        this.eventProcessingService = eventProcessingService;
        this.metricsService = metricsService;
    }

    public OrchestrationResult orchestrate(String userMessage, String provider, String model) {
        return orchestrateInternal(userMessage, provider, model, null);
    }

    public OrchestrationResult orchestrateStreaming(String userMessage, String provider, String model, String streamId) {
        return orchestrateInternal(userMessage, provider, model, streamId);
    }

    private OrchestrationResult orchestrateInternal(String userMessage, String provider, String model, @Nullable String streamId) {
        OrchestrationSession session = persistenceService.startSession(userMessage, provider, model);
        try {
            eventProcessingService.emitSession(streamId, session.getId().toString());
            eventProcessingService.emitStatus(streamId, "Starting orchestration");
            if (handleCancellation(streamId, session, "Cancelled")) {
                return new OrchestrationResult(new OrchestratorPlan(userMessage, List.of()), List.of(), "Cancelled.");
            }
            boolean requiresEdits = fileEditDetectionService.requiresFileEdits(userMessage);
            eventProcessingService.emitStatus(streamId, "Selecting roles");
            List<String> initialRoles = taskManagementService.selectRoles(session, userMessage, requiresEdits, null, provider, model);
            List<WorkerResult> advisoryResults = new ArrayList<>();
            List<TaskSpec> advisoryTasks = new ArrayList<>();

            TaskSpec contextTask = taskManagementService.buildContextSyncTask(initialRoles);
            String contextSyncContext = null;
            if (contextTask != null) {
                advisoryTasks.add(contextTask);
                eventProcessingService.emitTaskStart(streamId, contextTask);
                WorkerResult contextResult = skillExecutionService.runWorker(session, userMessage, contextTask, false, null, provider, model,
                        false, false, null, streamId);
                advisoryResults.add(contextResult);
                contextSyncContext = orchestrationContextService.buildResultsContext(List.of(contextResult));
            }

            AdvisoryBundle analysisBundle = taskManagementService.runAnalysisRounds(session, userMessage, initialRoles, requiresEdits,
                    provider, model, contextSyncContext, streamId);
            if (!analysisBundle.results().isEmpty()) {
                advisoryResults.addAll(analysisBundle.results());
                advisoryTasks.addAll(analysisBundle.tasks());
            }

            TaskSpec designTask = taskManagementService.buildDesignTask(userMessage, initialRoles);
            if (designTask != null) {
                advisoryTasks.add(designTask);
                String analysisContext = orchestrationContextService.buildResultsContext(analysisBundle.results());
                String designContext = orchestrationContextService.mergeContexts(contextSyncContext, analysisContext);
                eventProcessingService.emitTaskStart(streamId, designTask);
                advisoryResults.add(skillExecutionService.runWorker(session, userMessage, designTask, requiresEdits, designContext, provider, model,
                        true, false, null, streamId));
            }

            if (!advisoryTasks.isEmpty()) {
                log.info("Advisory tasks scheduled: {}.", advisoryTasks.size());
            }
            String advisoryContext = orchestrationContextService.mergeContexts(
                    contextSyncContext,
                    orchestrationContextService.buildAdvisoryContext(advisoryResults));

            eventProcessingService.emitStatus(streamId, "Generating plan");
            List<String> executionRoles = taskManagementService.selectRoles(session, userMessage, requiresEdits, advisoryContext, provider, model);
            OrchestratorPlan initialPlan = taskManagementService.requestPlan(session, userMessage, requiresEdits, executionRoles,
                    advisoryContext, provider, model, true, false);
            eventProcessingService.emitPlan(streamId, initialPlan);

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
                eventProcessingService.emitStatus(streamId, "Executing tasks (iteration " + (iteration + 1) + ")");
                List<WorkerResult> iterationResults = taskManagementService.executePlanTasks(session, userMessage, currentPlan.tasks(),
                        requiresEdits, advisoryContext, allResults, provider, model, taskIndex, streamId);
                allResults.addAll(iterationResults);
                allTasks.addAll(currentPlan.tasks());

                List<FailureDetail> failures = taskManagementService.collectFailures(iterationResults, currentPlan.tasks());
                String errorSummary = taskManagementService.buildErrorSummary(failures);
                OrchestratorPlan continuationPlan = taskManagementService.requestContinuationPlan(session, userMessage, requiresEdits,
                        executionRoles, advisoryContext, errorSummary, currentPlan, allResults, provider, model, true, true);
                if (continuationPlan.tasks().isEmpty() && !failures.isEmpty()) {
                    continuationPlan = taskManagementService.sanitizePlan(taskManagementService.buildRetryPlan(userMessage, failures),
                            userMessage, requiresEdits, executionRoles, true, true);
                }
                if (continuationPlan.tasks().isEmpty()) {
                    break;
                }
                var contPlanLog = persistenceService.logPlan(session, continuationPlan, false);
                taskIndex.putAll(persistenceService.logTasks(contPlanLog, continuationPlan.tasks()));
                currentPlan = continuationPlan;
                eventProcessingService.emitPlanUpdate(streamId, currentPlan);
                iteration++;
            }

            String objective = (initialPlan != null && StringUtils.hasText(initialPlan.objective()))
                    ? initialPlan.objective()
                    : userMessage;
            OrchestratorPlan finalPlan = new OrchestratorPlan(objective, allTasks);
            eventProcessingService.emitStatus(streamId, "Synthesizing response");
            String finalAnswer = agentInvocationService.synthesize(session, userMessage, finalPlan, allResults, provider, model);
            persistenceService.completeSession(session, finalAnswer, "COMPLETED");
            eventProcessingService.emitFinalAnswer(streamId, finalAnswer);
            eventProcessingService.emitRunComplete(streamId, "COMPLETED");
            metricsService.logSummary();
            return new OrchestrationResult(finalPlan, allResults, finalAnswer);
        } finally {
            // no thread-local state to clear
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
        try {
            eventProcessingService.emitSession(streamId, session.getId().toString());
            eventProcessingService.emitStatus(streamId, "Starting discovery");
            if (handleCancellation(streamId, session, "Cancelled")) {
                OrchestratorPlan emptyPlan = new OrchestratorPlan(userMessage, List.of());
                return new PlanDraft("", session.getId().toString(), emptyPlan, List.of(), "CANCELLED");
            }
            boolean requiresEdits = fileEditDetectionService.requiresFileEdits(userMessage);
            DiscoveryBundle discovery = taskManagementService.runDiscovery(session, userMessage, provider, model, streamId);
            if (handleCancellation(streamId, session, "Cancelled")) {
                OrchestratorPlan emptyPlan = new OrchestratorPlan(userMessage, List.of());
                return new PlanDraft("", session.getId().toString(), emptyPlan, discovery.results(), "CANCELLED");
            }
            String discoveryContext = orchestrationContextService.buildResultsContext(discovery.results());

            eventProcessingService.emitStatus(streamId, "Generating task plan");
            if (handleCancellation(streamId, session, "Cancelled")) {
                OrchestratorPlan emptyPlan = new OrchestratorPlan(userMessage, List.of());
                return new PlanDraft("", session.getId().toString(), emptyPlan, discovery.results(), "CANCELLED");
            }
            List<String> selectedRoles = taskManagementService.selectRoles(session, userMessage, requiresEdits, discoveryContext, provider, model);
            OrchestratorPlan sanitized = taskManagementService.requestPlan(session, userMessage, requiresEdits, selectedRoles,
                    discoveryContext, provider, model, false, false);
            OrchestratorPlanLog planLog = persistenceService.logPlan(session, sanitized, true);
            persistenceService.logTasks(planLog, sanitized.tasks());
            persistenceService.completeSession(session, null, "AWAITING_APPROVAL");

            PlanDraft draft = new PlanDraft(planLog.getId().toString(), session.getId().toString(),
                    sanitized, discovery.results(), "AWAITING_APPROVAL");
            eventProcessingService.emitPlanDraft(streamId, draft);
            eventProcessingService.emitStatus(streamId, "Awaiting approval");
            eventProcessingService.emitRunComplete(streamId, "AWAITING_APPROVAL");
            return draft;
        } finally {
            // no thread-local state to clear
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
        try {
            eventProcessingService.emitSession(streamId, session.getId().toString());
            eventProcessingService.emitStatus(streamId, "Executing approved plan");
            if (handleCancellation(streamId, session, "Cancelled")) {
                OrchestratorPlan cancelledPlan = taskManagementService.planFromLog(planLog);
                return new OrchestrationResult(cancelledPlan, List.of(), "Cancelled.");
            }
            String userMessage = session.getUserPrompt();
            if (StringUtils.hasText(feedback)) {
                userMessage = userMessage + "\n\nUser feedback:\n" + feedback.trim();
            }
            boolean requiresEdits = fileEditDetectionService.requiresFileEdits(session.getUserPrompt());
            OrchestratorPlan plan = taskManagementService.planFromLog(planLog);
            Map<String, TaskLog> taskIndex = taskManagementService.taskIndexFromPlanLog(planLog);
            List<WorkerResult> results = taskManagementService.executeApprovedPlanTasks(session, userMessage, plan.tasks(),
                    requiresEdits, provider, model, taskIndex, streamId);
            if (handleCancellation(streamId, session, "Cancelled")) {
                return new OrchestrationResult(plan, results, "Cancelled.");
            }
            String finalAnswer = agentInvocationService.synthesize(session, userMessage, plan, results, provider, model);
            persistenceService.completeSession(session, finalAnswer, "COMPLETED");
            eventProcessingService.emitFinalAnswer(streamId, finalAnswer);
            eventProcessingService.emitRunComplete(streamId, "COMPLETED");
            metricsService.logSummary();
            return new OrchestrationResult(plan, results, finalAnswer);
        } finally {
            // no thread-local state to clear
        }
    }

    private boolean handleCancellation(@Nullable String streamId, OrchestrationSession session, String statusMessage) {
        if (!eventProcessingService.isCancelled(streamId)) {
            return false;
        }
        eventProcessingService.emitStatus(streamId, statusMessage);
        eventProcessingService.emitRunComplete(streamId, "CANCELLED");
        persistenceService.completeSession(session, null, "CANCELLED");
        return true;
    }
}
