package com.bko.orchestration.api;

import com.bko.entity.OrchestrationSession;
import com.bko.orchestration.model.OrchestratorPlan;
import com.bko.orchestration.model.TaskSpec;
import com.bko.orchestration.model.WorkerResult;
import com.bko.orchestration.service.ToolAccessPolicy;
import com.bko.orchestration.service.WorkerCallResult;
import org.springframework.lang.Nullable;

import java.util.List;

/**
 * Service interface for invoking agent operations, including plan requests, role selection,
 * and worker prompt execution.
 */
public interface AgentInvocationService {

    /**
     * Requests an initial orchestration plan from an agent.
     *
     * @param session The current orchestration session.
     * @param userMessage The message from the user.
     * @param allowedRoles A list of roles allowed for this plan.
     * @param context Additional context for the plan request.
     * @param provider The AI model provider.
     * @param model The AI model to use.
     * @return An {@link OrchestratorPlan} detailing the steps to be taken.
     */
    OrchestratorPlan requestPlan(OrchestrationSession session,
                                 String userMessage,
                                 List<String> allowedRoles,
                                 @Nullable String context,
                                 String provider,
                                 String model);

    /**
     * Requests a continuation plan from an agent, typically after an initial plan or an error.
     *
     * @param session The current orchestration session.
     * @param userMessage The message from the user.
     * @param allowedRoles A list of roles allowed for this plan.
     * @param context Additional context for the plan request.
     * @param errorSummary A summary of any errors encountered.
     * @param plan The existing {@link OrchestratorPlan} to continue from.
     * @param results A list of {@link WorkerResult} from previous steps.
     * @param provider The AI model provider.
     * @param model The AI model to use.
     * @return A new {@link OrchestratorPlan} for continuation.
     */
    OrchestratorPlan requestContinuationPlan(OrchestrationSession session,
                                             String userMessage,
                                             List<String> allowedRoles,
                                             @Nullable String context,
                                             @Nullable String errorSummary,
                                             OrchestratorPlan plan,
                                             List<WorkerResult> results,
                                             String provider,
                                             String model);

    /**
     * Requests a minimal set of skills for a given task.
     *
     * @param session The current orchestration session.
     * @param userMessage The message from the user.
     * @param task The {@link TaskSpec} defining the task.
     * @param skills A list of available skills (name + description).
     * @param budget The maximum number of skills to select.
     * @param context Additional context for the request.
     * @param provider The AI model provider.
     * @param model The AI model to use.
     * @return A {@link com.bko.orchestration.model.SkillSelection} containing chosen skills.
     */
    com.bko.orchestration.model.SkillSelection requestSkillSelection(OrchestrationSession session,
                                                                     String userMessage,
                                                                     TaskSpec task,
                                                                     List<com.bko.orchestration.model.SkillSummary> skills,
                                                                     int budget,
                                                                     @Nullable String context,
                                                                     String provider,
                                                                     String model);

    /**
     * Runs a worker prompt to execute a specific task.
     *
     * @param session The current orchestration session.
     * @param systemPrompt The system-level prompt for the worker.
     * @param userMessage The message from the user.
     * @param task The {@link TaskSpec} defining the task to be executed.
     * @param normalizedContext The normalized context for the worker.
     * @param provider The AI model provider.
     * @param model The AI model to use.
     * @param phase The {@link ToolAccessPolicy.Phase} for tool access.
     * @return A {@link WorkerCallResult} containing the result of the worker's execution.
     */
    WorkerCallResult runWorkerPrompt(OrchestrationSession session,
                                     String systemPrompt,
                                     String userMessage,
                                     TaskSpec task,
                                     String normalizedContext,
                                     String provider,
                                     String model,
                                     ToolAccessPolicy.Phase phase);

}
