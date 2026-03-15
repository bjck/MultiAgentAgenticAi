package com.bko.orchestration.service;

import com.bko.entity.*;
import com.bko.orchestration.model.OrchestratorPlan;
import com.bko.orchestration.model.TaskSpec;
import com.bko.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.lang.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrchestrationPersistenceService {

    private final OrchestrationSessionRepository sessionRepository;
    private final PromptLogRepository promptLogRepository;
    private final OrchestratorPlanLogRepository planLogRepository;
    private final TaskLogRepository taskLogRepository;
    private final WorkerResultLogRepository workerResultLogRepository;
    private final ToolCallLogRepository toolCallLogRepository;
    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private volatile Boolean promptLogTokenColumnsAvailable;

    /**
     * Creates and persists an orchestration session in its own transaction so the row
     * is committed before any dependent inserts (e.g. worker_result_log). This avoids
     * FK constraint violations when orchestration runs in a different thread or when
     * the outer transaction is rolled back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrchestrationSession startSession(String userPrompt, @Nullable String provider, @Nullable String model) {
        OrchestrationSession session = OrchestrationSession.builder()
                .userPrompt(userPrompt)
                .provider(provider)
                .model(model)
                .status("IN_PROGRESS")
                .build();
        return sessionRepository.saveAndFlush(session);
    }

    public void completeSession(OrchestrationSession session, @Nullable String finalAnswer, String status) {
        session.setFinalAnswer(finalAnswer);
        session.setStatus(status);
        sessionRepository.save(session);
    }

    /**
     * Persist a single prompt log in its own transaction so it is never lost
     * to rollback of a surrounding orchestration transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logPrompt(OrchestrationSession session, String purpose, @Nullable String role,
                          @Nullable String systemPrompt, @Nullable String userTemplate,
                          Map<String, String> params, @Nullable String fullResponse,
                          @Nullable Integer inputTokens, @Nullable Integer outputTokens) {
        String userPrompt = userTemplate == null ? null : fillTemplate(userTemplate, params);
        Integer safeInputTokens = supportsPromptLogTokenColumns() ? inputTokens : null;
        Integer safeOutputTokens = supportsPromptLogTokenColumns() ? outputTokens : null;
        PromptLog log = PromptLog.builder()
                .session(session)
                .purpose(purpose)
                .role(role)
                .systemPrompt(systemPrompt)
                .userPrompt(userPrompt)
                .fullResponse(fullResponse)
                .inputTokenCount(safeInputTokens)
                .outputTokenCount(safeOutputTokens)
                .build();
        promptLogRepository.saveAndFlush(log);
    }

    /**
     * Persist the plan log in its own transaction so dependent task logs can
     * safely reference it across threads or surrounding transactions.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrchestratorPlanLog logPlan(OrchestrationSession session, OrchestratorPlan plan, boolean isInitial) {
        OrchestratorPlanLog pl = OrchestratorPlanLog.builder()
                .session(session)
                .objective(plan.objective())
                .initial(isInitial)
                .build();
        return planLogRepository.saveAndFlush(pl);
    }

    /**
     * Persist task logs in a new transaction to ensure they are committed before
     * worker threads log tool calls or results that reference them.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Map<String, TaskLog> logTasks(OrchestratorPlanLog planLog, List<TaskSpec> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return Map.of();
        }
        List<TaskLog> toSave = new ArrayList<>(tasks.size());
        Map<String, TaskLog> index = new LinkedHashMap<>();
        for (TaskSpec t : tasks) {
            TaskLog tl = TaskLog.builder()
                    .plan(planLog)
                    .taskIdAlias(t.id())
                    .role(t.role())
                    .description(t.description())
                    .expectedOutput(t.expectedOutput())
                    .build();
            toSave.add(tl);
            if (t.id() != null) {
                index.put(t.id(), tl);
            }
        }
        taskLogRepository.saveAll(toSave);
        taskLogRepository.flush();
        return index;
    }

    public WorkerResultLog logWorkerResult(OrchestrationSession session, @Nullable TaskLog taskLog, @Nullable String role, String output) {
        WorkerResultLog wr = WorkerResultLog.builder()
                .session(session)
                .taskLog(taskLog)
                .role(role)
                .output(output)
                .build();
        return workerResultLogRepository.save(wr);
    }

    public ToolCallLog logToolCall(OrchestrationSession session, @Nullable TaskLog taskLog, @Nullable String role,
                                   String toolName, @Nullable String toolInput, @Nullable String toolOutput) {
        ToolCallLog log = ToolCallLog.builder()
                .session(session)
                .taskLog(taskLog)
                .role(role)
                .toolName(toolName)
                .toolInput(toolInput)
                .toolOutput(toolOutput)
                .build();
        return toolCallLogRepository.save(log);
    }

    public Optional<OrchestratorPlanLog> findPlanWithTasks(String planId) {
        if (!StringUtils.hasText(planId)) {
            return Optional.empty();
        }
        try {
            UUID id = UUID.fromString(planId);
            return planLogRepository.findWithTasksById(id);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private String fillTemplate(String template, Map<String, String> params) {
        String out = template;
        if (params != null) {
            for (Map.Entry<String, String> e : params.entrySet()) {
                String key = "{" + e.getKey() + "}";
                out = out.replace(key, e.getValue() == null ? "" : e.getValue());
            }
        }
        return out;
    }

    /**
     * Backward-compatibility for environments where Liquibase migrations are disabled
     * or not yet applied: if token columns are missing, store prompt logs without
     * token fields instead of failing prompt_log inserts.
     */
    private boolean supportsPromptLogTokenColumns() {
        Boolean cached = promptLogTokenColumnsAvailable;
        if (cached != null) {
            return cached;
        }
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            promptLogTokenColumnsAvailable = false;
            return false;
        }
        try {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'prompt_log'
                      AND column_name IN ('input_token_count', 'output_token_count')
                    """, Integer.class);
            boolean available = count != null && count >= 2;
            promptLogTokenColumnsAvailable = available;
            return available;
        } catch (Exception ex) {
            log.warn("Failed to inspect prompt_log token columns; falling back to no-token prompt logging.", ex);
            promptLogTokenColumnsAvailable = false;
            return false;
        }
    }
}
