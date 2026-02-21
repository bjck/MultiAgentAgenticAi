package com.bko.orchestration.api;

import com.bko.orchestration.model.OrchestratorPlan;
import com.bko.orchestration.model.PlanDraft;
import com.bko.orchestration.model.TaskSpec;
import com.bko.orchestration.model.WorkerResult;
import org.springframework.lang.Nullable;

/**
 * Service interface for processing and emitting various events throughout the orchestration process.
 * This includes status updates, task lifecycle events, plan updates, and error notifications.
 */
public interface EventProcessingService {

    /**
     * Checks if a given stream has been cancelled.
     *
     * @param streamId The ID of the stream to check.
     * @return {@code true} if the stream is cancelled, {@code false} otherwise.
     */
    boolean isCancelled(@Nullable String streamId);

    /**
     * Emits a session ID to a specified stream.
     *
     * @param streamId The ID of the stream to emit to.
     * @param sessionId The session ID to emit.
     */
    void emitSession(@Nullable String streamId, String sessionId);

    /**
     * Emits a status update to a specified stream.
     *
     * @param streamId The ID of the stream to emit to.
     * @param status The status message to emit.
     */
    void emitStatus(@Nullable String streamId, String status);

    /**
     * Emits a task start event to a specified stream.
     *
     * @param streamId The ID of the stream to emit to.
     * @param task The {@link TaskSpec} representing the task that has started.
     */
    void emitTaskStart(@Nullable String streamId, TaskSpec task);

    /**
     * Emits task output to a specified stream.
     *
     * @param streamId The ID of the stream to emit to.
     * @param result The {@link WorkerResult} containing the output of the task.
     */
    void emitTaskOutput(@Nullable String streamId, WorkerResult result);

    /**
     * Emits a task completion event to a specified stream.
     *
     * @param streamId The ID of the stream to emit to.
     * @param result The {@link WorkerResult} containing the final result of the completed task.
     */
    void emitTaskComplete(@Nullable String streamId, WorkerResult result);

    /**
     * Emits an orchestration plan to a specified stream.
     *
     * @param streamId The ID of the stream to emit to.
     * @param plan The {@link OrchestratorPlan} to emit.
     */
    void emitPlan(@Nullable String streamId, OrchestratorPlan plan);

    /**
     * Emits an update to an existing orchestration plan to a specified stream.
     *
     * @param streamId The ID of the stream to emit to.
     * @param plan The updated {@link OrchestratorPlan} to emit.
     */
    void emitPlanUpdate(@Nullable String streamId, OrchestratorPlan plan);

    /**
     * Emits a draft of an orchestration plan to a specified stream.
     *
     * @param streamId The ID of the stream to emit to.
     * @param draft The {@link PlanDraft} to emit.
     */
    void emitPlanDraft(@Nullable String streamId, PlanDraft draft);

    /**
     * Emits the final answer or result to a specified stream.
     *
     * @param streamId The ID of the stream to emit to.
     * @param answer The final answer string to emit.
     */
    void emitFinalAnswer(@Nullable String streamId, String answer);

    /**
     * Emits a run completion event to a specified stream.
     *
     * @param streamId The ID of the stream to emit to.
     * @param status The final status of the run.
     */
    void emitRunComplete(@Nullable String streamId, String status);

    /**
     * Emits an error message to a specified stream.
     *
     * @param streamId The ID of the stream to emit to.
     * @param message The error message to emit.
     */
    void emitError(@Nullable String streamId, String message);
}
