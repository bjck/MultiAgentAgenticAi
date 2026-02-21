package com.bko.orchestration.api;

import com.bko.entity.OrchestrationSession;
import com.bko.orchestration.collaboration.CollaborationStage;
import com.bko.orchestration.collaboration.CollaborationStrategy;
import com.bko.orchestration.model.OrchestratorPlan;
import com.bko.orchestration.model.RoleSelection;
import com.bko.orchestration.model.TaskSpec;
import com.bko.orchestration.model.WorkerResult;
import com.bko.orchestration.service.ToolAccessPolicy;
import com.bko.orchestration.service.WorkerCallResult;
import org.springframework.lang.Nullable;

import java.util.List;

/**
 * Service interface for invoking agent operations, including plan requests, role selection,
 * worker prompt execution, collaboration rounds, and synthesis.
 */
public interface AgentInvocationService {

    /**
     * Requests an initial orchestration plan from an agent.
     *
     * @param session The current orchestration session.
     * @param userMessage The message from the user.
     * @param requiresEdits Indicates if the plan requires edits.
     * @param allowedRoles A list of roles allowed for this plan.
     * @param context Additional context for the plan request.
     * @param provider The AI model provider.
     * @param model The AI model to use.
     * @return An {@link OrchestratorPlan} detailing the steps to be taken.
     */
    OrchestratorPlan requestPlan(OrchestrationSession session,
                                 String userMessage,
                                 boolean requiresEdits,
                                 List<String> allowedRoles,
                                 @Nullable String context,
                                 String provider,
                                 String model);

    /**
     * Requests a continuation plan from an agent, typically after an initial plan or an error.
     *
     * @param session The current orchestration session.
     * @param userMessage The message from the user.
     * @param requiresEdits Indicates if the plan requires edits.
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
                                             boolean requiresEdits,
                                             List<String> allowedRoles,
                                             @Nullable String context,
                                             @Nullable String errorSummary,
                                             OrchestratorPlan plan,
                                             List<WorkerResult> results,
                                             String provider,
                                             String model);

    /**
     * Requests an agent to select a role based on the current context.
     *
     * @param session The current orchestration session.
     * @param userMessage The message from the user.
     * @param requiresEdits Indicates if the role selection process requires edits.
     * @param availableRoles A list of roles available for selection.
     * @param context Additional context for role selection.
     * @param provider The AI model provider.
     * @param model The AI model to use.
     * @return A {@link RoleSelection} object indicating the chosen role.
     */
    RoleSelection requestRoleSelection(OrchestrationSession session,
                                       String userMessage,
                                       boolean requiresEdits,
                                       List<String> availableRoles,
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

    /**
     * Executes a collaboration round between agents.
     *
     * @param session The current orchestration session.
     * @param userMessage The message from the user.
     * @param task The {@link TaskSpec} for the collaboration.
     * @param round The current round number of collaboration.
     * @param stage The current {@link CollaborationStage}.
     * @param strategy The {@link CollaborationStrategy} being used.
     * @param finalStage Indicates if this is the final stage of collaboration.
     * @param results A list of {@link WorkerResult} from previous collaboration steps.
     * @param provider The AI model provider.
     * @param model The AI model to use.
     * @return A string representing the outcome or message from the collaboration round.
     */
    String collaborateRound(OrchestrationSession session,
                            String userMessage,
                            TaskSpec task,
                            int round,
                            CollaborationStage stage,
                            CollaborationStrategy strategy,
                            boolean finalStage,
                            List<WorkerResult> results,
                            String provider,
                            String model);

    /**
     * Synthesizes information or results from an orchestration plan and worker results.
     *
     * @param session The current orchestration session.
     * @param userMessage The message from the user.
     * @param plan The {@link OrchestratorPlan} that was executed.
     * @param results A list of {@link WorkerResult} to synthesize.
     * @param provider The AI model provider.
     * @param model The AI model to use.
     * @return A synthesized string result.
     */
    String synthesize(OrchestrationSession session,
                      String userMessage,
                      OrchestratorPlan plan,
                      List<WorkerResult> results,
                      String provider,
                      String model);
}
