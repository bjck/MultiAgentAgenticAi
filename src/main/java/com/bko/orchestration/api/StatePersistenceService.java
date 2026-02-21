package com.bko.orchestration.api;

import com.bko.entity.OrchestrationSession;
import com.bko.entity.OrchestratorPlanLog;
import com.bko.entity.TaskLog;
import com.bko.entity.ToolCallLog;
import com.bko.entity.WorkerResultLog;
import com.bko.orchestration.model.OrchestratorPlan;
import com.bko.orchestration.model.TaskSpec;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service interface for persisting the state of orchestration sessions, plans, tasks,
 * worker results, and tool calls. This ensures that the progress and outcomes of
 * various operations are recorded and can be retrieved.
 */
public interface StatePersistenceService {

    /**
     * Starts a new orchestration session and persists its initial state.
     *
     * @param userPrompt The initial prompt provided by the user.
     * @param provider An optional AI model provider used for the session.
     * @param model An optional AI model used for the session.
     * @return The newly created and persisted {@link OrchestrationSession}.
     */
    OrchestrationSession startSession(String userPrompt, @Nullable String provider, @Nullable String model);

    /**
     * Completes an existing orchestration session, updating its final status and answer.
     *
     * @param session The {@link OrchestrationSession} to complete.
     * @param finalAnswer An optional final answer or result of the session.
     * @param status The final status of the session (e.g., "completed", "failed").
     */
    void completeSession(OrchestrationSession session, @Nullable String finalAnswer, String status);

    /**
     * Logs details of a prompt used during an orchestration session.
     *
     * @param session The {@link OrchestrationSession} during which the prompt was used.
     * @param purpose The purpose of the prompt (e.g., "plan request", "task execution").
     * @param role An optional role associated with the prompt.
     * @param systemPrompt An optional system-level prompt content.
     * @param userTemplate An optional user message template.
     * @param params A map of parameters used to fill the user template.
     * @param fullResponse An optional full response received from the AI model.
     */
    void logPrompt(OrchestrationSession session,
                   String purpose,
                   @Nullable String role,
                   @Nullable String systemPrompt,
                   @Nullable String userTemplate,
                   Map<String, String> params,
                   @Nullable String fullResponse);

    /**
     * Logs an orchestration plan.
     *
     * @param session The {@link OrchestrationSession} to which the plan belongs.
     * @param plan The {@link OrchestratorPlan} to log.
     * @param isInitial A boolean indicating if this is the initial plan for the session.
     * @return The persisted {@link OrchestratorPlanLog}.
     */
    OrchestratorPlanLog logPlan(OrchestrationSession session, OrchestratorPlan plan, boolean isInitial);

    /**
     * Logs a list of tasks associated with a specific orchestration plan log.
     *
     * @param planLog The {@link OrchestratorPlanLog} to which these tasks belong.
     * @param tasks A list of {@link TaskSpec} to log.
     * @return A map of task IDs to their corresponding persisted {@link TaskLog} objects.
     */
    Map<String, TaskLog> logTasks(OrchestratorPlanLog planLog, List<TaskSpec> tasks);

    /**
     * Logs the result of a worker's execution.
     *
     * @param session The {@link OrchestrationSession} during which the worker was run.
     * @param taskLog An optional {@link TaskLog} associated with the worker's task.
     * @param role An optional role associated with the worker.
     * @param output The output produced by the worker.
     * @return The persisted {@link WorkerResultLog}.
     */
    WorkerResultLog logWorkerResult(OrchestrationSession session, @Nullable TaskLog taskLog, @Nullable String role, String output);

    /**
     * Logs a tool call made during an orchestration session.
     *
     * @param session The {@link OrchestrationSession} during which the tool call was made.
     * @param taskLog An optional {@link TaskLog} associated with the task that made the tool call.
     * @param role An optional role associated with the tool call.
     * @param toolName The name of the tool called.
     * @param toolInput An optional input provided to the tool.
     * @param toolOutput An optional output received from the tool.
     * @return The persisted {@link ToolCallLog}.
     */
    ToolCallLog logToolCall(OrchestrationSession session, @Nullable TaskLog taskLog, @Nullable String role,
                            String toolName, @Nullable String toolInput, @Nullable String toolOutput);

    /**
     * Finds an orchestration plan along with its associated tasks by plan ID.
     *
     * @param planId The ID of the plan to find.
     * @return An {@link Optional} containing the {@link OrchestratorPlanLog} if found, otherwise empty.
     */
    Optional<OrchestratorPlanLog> findPlanWithTasks(String planId);
}
