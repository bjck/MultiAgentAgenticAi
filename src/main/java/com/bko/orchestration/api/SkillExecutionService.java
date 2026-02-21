package com.bko.orchestration.api;

import com.bko.entity.OrchestrationSession;
import com.bko.entity.TaskLog;
import com.bko.orchestration.model.TaskSpec;
import com.bko.orchestration.model.WorkerResult;
import org.springframework.lang.Nullable;

/**
 * Service interface for executing skills or tasks through workers, including both
 * independent worker runs and collaborative tasks.
 */
public interface SkillExecutionService {

    /**
     * Runs a worker to execute a specific task.
     *
     * @param session The current orchestration session.
     * @param userMessage The message from the user, providing context or instructions.
     * @param task The {@link TaskSpec} defining the task to be executed.
     * @param requiresEdits Indicates if the task execution might require subsequent edits or refinements.
     * @param context Additional context relevant to the task execution.
     * @param provider The AI model provider to use for the worker.
     * @param model The specific AI model to use.
     * @param includeHandoffSchema Flag to indicate if the handoff schema should be included.
     * @param requireToolCalls Flag to indicate if the worker is required to make tool calls.
     * @param taskLog An optional {@link TaskLog} to record task execution details.
     * @param streamId An optional stream ID for real-time event emission.
     * @return A {@link WorkerResult} containing the outcome of the worker's execution.
     */
    WorkerResult runWorker(OrchestrationSession session,
                           String userMessage,
                           TaskSpec task,
                           boolean requiresEdits,
                           @Nullable String context,
                           String provider,
                           String model,
                           boolean includeHandoffSchema,
                           boolean requireToolCalls,
                           @Nullable TaskLog taskLog,
                           @Nullable String streamId);

    /**
     * Runs a collaborative task involving multiple agents or steps.
     *
     * @param session The current orchestration session.
     * @param userMessage The message from the user, providing context or instructions.
     * @param task The {@link TaskSpec} defining the collaborative task.
     * @param requiresEdits Indicates if the task execution might require subsequent edits or refinements.
     * @param baseContext The base context for the collaborative task.
     * @param provider The AI model provider to use.
     * @param model The specific AI model to use.
     * @param taskLog An optional {@link TaskLog} to record task execution details.
     * @param streamId An optional stream ID for real-time event emission.
     * @return A {@link WorkerResult} containing the outcome of the collaborative task.
     */
    WorkerResult runCollaborativeTask(OrchestrationSession session,
                                      String userMessage,
                                      TaskSpec task,
                                      boolean requiresEdits,
                                      @Nullable String baseContext,
                                      String provider,
                                      String model,
                                      @Nullable TaskLog taskLog,
                                      @Nullable String streamId);
}
