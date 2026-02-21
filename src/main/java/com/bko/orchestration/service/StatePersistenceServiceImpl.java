package com.bko.orchestration.service;

import com.bko.entity.OrchestrationSession;
import com.bko.entity.OrchestratorPlanLog;
import com.bko.entity.TaskLog;
import com.bko.entity.ToolCallLog;
import com.bko.entity.WorkerResultLog;
import com.bko.orchestration.api.StatePersistenceService;
import com.bko.orchestration.model.OrchestratorPlan;
import com.bko.orchestration.model.TaskSpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class StatePersistenceServiceImpl implements StatePersistenceService {

    private final OrchestrationPersistenceService persistenceService;

    public StatePersistenceServiceImpl(OrchestrationPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @Override
    public OrchestrationSession startSession(String userPrompt, @Nullable String provider, @Nullable String model) {
        return persistenceService.startSession(userPrompt, provider, model);
    }

    @Override
    public void completeSession(OrchestrationSession session, @Nullable String finalAnswer, String status) {
        persistenceService.completeSession(session, finalAnswer, status);
    }

    @Override
    public void logPrompt(OrchestrationSession session, String purpose, @Nullable String role,
                          @Nullable String systemPrompt, @Nullable String userTemplate,
                          Map<String, String> params, @Nullable String fullResponse) {
        try {
            persistenceService.logPrompt(session, purpose, role, systemPrompt, userTemplate, params, fullResponse);
        } catch (Exception ex) {
            log.debug("Failed to log prompt {}: {}", purpose, ex.getMessage());
        }
    }

    @Override
    public OrchestratorPlanLog logPlan(OrchestrationSession session, OrchestratorPlan plan, boolean isInitial) {
        return persistenceService.logPlan(session, plan, isInitial);
    }

    @Override
    public Map<String, TaskLog> logTasks(OrchestratorPlanLog planLog, List<TaskSpec> tasks) {
        return persistenceService.logTasks(planLog, tasks);
    }

    @Override
    public WorkerResultLog logWorkerResult(OrchestrationSession session, @Nullable TaskLog taskLog, @Nullable String role, String output) {
        try {
            return persistenceService.logWorkerResult(session, taskLog, role, output);
        } catch (Exception ex) {
            log.debug("Failed to log worker result: {}", ex.getMessage());
            return null;
        }
    }

    @Override
    public ToolCallLog logToolCall(OrchestrationSession session, @Nullable TaskLog taskLog, @Nullable String role,
                                   String toolName, @Nullable String toolInput, @Nullable String toolOutput) {
        try {
            return persistenceService.logToolCall(session, taskLog, role, toolName, toolInput, toolOutput);
        } catch (Exception ex) {
            log.debug("Failed to log tool call {}: {}", toolName, ex.getMessage());
            return null;
        }
    }

    @Override
    public Optional<OrchestratorPlanLog> findPlanWithTasks(String planId) {
        return persistenceService.findPlanWithTasks(planId);
    }
}
