package com.bko.orchestration;

import static com.bko.orchestration.OrchestrationConstants.*;

import com.bko.entity.OrchestrationSession;
import com.bko.entity.OrchestratorPlanLog;
import com.bko.entity.TaskLog;
import com.bko.orchestration.api.EventProcessingService;
import com.bko.orchestration.api.StatePersistenceService;
import com.bko.orchestration.api.TaskManagementService;
import com.bko.orchestration.model.OrchestrationResult;
import com.bko.orchestration.model.OrchestratorPlan;
import com.bko.orchestration.model.PlanDraft;
import com.bko.orchestration.model.WorkerResult;
import com.bko.orchestration.service.OrchestrationMetricsService;
import com.bko.orchestration.service.SkillPlanningService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class OrchestratorService {

    private final TaskManagementService taskManagementService;
    private final SkillPlanningService skillPlanningService;
    private final StatePersistenceService persistenceService;
    private final EventProcessingService eventProcessingService;
    private final OrchestrationMetricsService metricsService;

    public OrchestratorService(TaskManagementService taskManagementService,
                               SkillPlanningService skillPlanningService,
                               StatePersistenceService persistenceService,
                               EventProcessingService eventProcessingService,
                               OrchestrationMetricsService metricsService) {
        this.taskManagementService = taskManagementService;
        this.skillPlanningService = skillPlanningService;
        this.persistenceService = persistenceService;
        this.eventProcessingService = eventProcessingService;
        this.metricsService = metricsService;
    }

    public OrchestrationResult orchestrate(String userMessage, String provider, String model) {
        return orchestrateInternal(userMessage, provider, model, null, null);
    }

    public OrchestrationResult orchestrateStreaming(String userMessage, String provider, String model, String streamId) {
        return orchestrateInternal(userMessage, provider, model, streamId, null);
    }

    public OrchestrationResult orchestrateWithSession(OrchestrationSession session, String userMessage, String provider, String model) {
        return orchestrateInternal(userMessage, provider, model, null, session);
    }

    private OrchestrationResult orchestrateInternal(String userMessage, String provider, String model,
                                                    @Nullable String streamId,
                                                    @Nullable OrchestrationSession existingSession) {
        OrchestrationSession session = existingSession != null
                ? existingSession
                : persistenceService.startSession(userMessage, provider, model);
        try {
            eventProcessingService.emitSession(streamId, session.getId().toString());
            eventProcessingService.emitStatus(streamId, "Starting orchestration");
            if (handleCancellation(streamId, session, "Cancelled")) {
                return new OrchestrationResult(new OrchestratorPlan(userMessage, List.of()), List.of(), "Cancelled.");
            }
            eventProcessingService.emitStatus(streamId, "Generating plan");
            OrchestratorPlan plan = taskManagementService.requestPlan(session, userMessage, List.of(ROLE_GENERAL),
                    null, provider, model, true, false);
            eventProcessingService.emitPlan(streamId, plan);

            if (plan.tasks().isEmpty()) {
                eventProcessingService.emitStatus(streamId, "No tasks to execute");
                String finalAnswer = "No tasks generated.";
                persistenceService.completeSession(session, finalAnswer, "COMPLETED");
                eventProcessingService.emitFinalAnswer(streamId, finalAnswer);
                eventProcessingService.emitRunComplete(streamId, "COMPLETED");
                metricsService.logSummary();
                return new OrchestrationResult(plan, List.of(), finalAnswer);
            }

            OrchestratorPlanLog planLog = persistenceService.logPlan(session, plan, true);
            Map<String, TaskLog> taskIndex = new HashMap<>();
            taskIndex.putAll(persistenceService.logTasks(planLog, plan.tasks()));

            if (handleCancellation(streamId, session, "Cancelled")) {
                return new OrchestrationResult(plan, List.of(), "Cancelled.");
            }
            eventProcessingService.emitStatus(streamId, "Executing tasks");
            List<WorkerResult> results = taskManagementService.executePlanTasks(session, userMessage, plan.tasks(),
                    null, List.of(), provider, model, taskIndex, streamId);

            String objective = StringUtils.hasText(plan.objective()) ? plan.objective() : userMessage;
            OrchestratorPlan finalPlan = new OrchestratorPlan(objective, plan.tasks());
            eventProcessingService.emitStatus(streamId, "Preparing response");
            String finalAnswer = formatResults(results);
            persistenceService.completeSession(session, finalAnswer, "COMPLETED");
            eventProcessingService.emitFinalAnswer(streamId, finalAnswer);
            eventProcessingService.emitRunComplete(streamId, "COMPLETED");
            metricsService.logSummary();
            return new OrchestrationResult(finalPlan, results, finalAnswer);
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
            eventProcessingService.emitStatus(streamId, "Starting planning");
            if (handleCancellation(streamId, session, "Cancelled")) {
                OrchestratorPlan emptyPlan = new OrchestratorPlan(userMessage, List.of());
                return new PlanDraft("", session.getId().toString(), emptyPlan, List.of(), List.of(), "CANCELLED");
            }
            eventProcessingService.emitStatus(streamId, "Generating task plan");
            if (handleCancellation(streamId, session, "Cancelled")) {
                OrchestratorPlan emptyPlan = new OrchestratorPlan(userMessage, List.of());
                return new PlanDraft("", session.getId().toString(), emptyPlan, List.of(), List.of(), "CANCELLED");
            }
            OrchestratorPlan sanitized = taskManagementService.requestPlan(session, userMessage, List.of(ROLE_GENERAL),
                    null, provider, model, false, false);
            OrchestratorPlanLog planLog = persistenceService.logPlan(session, sanitized, true);
            persistenceService.logTasks(planLog, sanitized.tasks());
            persistenceService.completeSession(session, null, "AWAITING_APPROVAL");

            var skillPlans = skillPlanningService.planForTasks(session, userMessage, sanitized.tasks(),
                    null, provider, model);
            PlanDraft draft = new PlanDraft(planLog.getId().toString(), session.getId().toString(),
                    sanitized, List.of(), skillPlans, "AWAITING_APPROVAL");
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
            OrchestratorPlan plan = taskManagementService.planFromLog(planLog);
            Map<String, TaskLog> taskIndex = taskManagementService.taskIndexFromPlanLog(planLog);
            List<WorkerResult> results = taskManagementService.executeApprovedPlanTasks(session, userMessage, plan.tasks(),
                    provider, model, taskIndex, streamId);
            if (handleCancellation(streamId, session, "Cancelled")) {
                return new OrchestrationResult(plan, results, "Cancelled.");
            }
            String finalAnswer = formatResults(results);
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

    private String formatResults(@Nullable List<WorkerResult> results) {
        if (results == null || results.isEmpty()) {
            return "No results produced.";
        }
        if (results.size() == 1) {
            String output = results.getFirst() != null ? results.getFirst().output() : "";
            return StringUtils.hasText(output) ? output : "No results produced.";
        }
        StringBuilder sb = new StringBuilder();
        for (WorkerResult result : results) {
            if (result == null) {
                continue;
            }
            String label = StringUtils.hasText(result.taskId()) ? result.taskId() : "task";
            if (StringUtils.hasText(result.role())) {
                label = label + " (" + result.role() + ")";
            }
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append(label).append(":\n").append(result.output() == null ? "" : result.output());
        }
        return sb.isEmpty() ? "No results produced." : sb.toString();
    }
}
