package com.bko.orchestration.api;

import com.bko.entity.OrchestrationSession;
import com.bko.entity.OrchestratorPlanLog;
import com.bko.entity.TaskLog;
import com.bko.orchestration.model.AdvisoryBundle;
import com.bko.orchestration.model.DiscoveryBundle;
import com.bko.orchestration.model.FailureDetail;
import com.bko.orchestration.model.OrchestratorPlan;
import com.bko.orchestration.model.TaskSpec;
import com.bko.orchestration.model.WorkerResult;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Service interface for managing tasks within an orchestration process, including
 * role selection, discovery, analysis, plan building, and task execution.
 */
public interface TaskManagementService {

    /**
     * Selects roles for an orchestration session based on the user message and context.
     *
     * @param session The current orchestration session.
     * @param userMessage The message from the user.
     * @param requiresEdits Indicates if the role selection process requires edits.
     * @param context Additional context for role selection.
     * @param provider The AI model provider.
     * @param model The AI model to use.
     * @return A list of selected role names.
     */
    List<String> selectRoles(OrchestrationSession session,
                             String userMessage,
                             boolean requiresEdits,
                             @Nullable String context,
                             String provider,
                             String model);

    /**
     * Runs a discovery phase to gather information relevant to the user's request.
     *
     * @param session The current orchestration session.
     * @param userMessage The message from the user.
     * @param provider The AI model provider.
     * @param model The AI model to use.
     * @param streamId An optional stream ID for real-time event emission.
     * @return A {@link DiscoveryBundle} containing the results of the discovery phase.
     */
    DiscoveryBundle runDiscovery(OrchestrationSession session,
                                 String userMessage,
                                 String provider,
                                 String model,
                                 @Nullable String streamId);

    /**
     * Runs analysis rounds involving selected roles to refine understanding or generate insights.
     *
     * @param session The current orchestration session.
     * @param userMessage The message from the user.
     * @param selectedRoles A list of roles participating in the analysis.
     * @param requiresEdits Indicates if the analysis rounds require edits.
     * @param provider The AI model provider.
     * @param model The AI model to use.
     * @param baseContext The base context for the analysis.
     * @param streamId An optional stream ID for real-time event emission.
     * @return An {@link AdvisoryBundle} containing advisory information from the analysis.
     */
    AdvisoryBundle runAnalysisRounds(OrchestrationSession session,
                                     String userMessage,
                                     List<String> selectedRoles,
                                     boolean requiresEdits,
                                     String provider,
                                     String model,
                                     @Nullable String baseContext,
                                     @Nullable String streamId);

    /**
     * Builds a design task based on the user message and selected roles.
     *
     * @param userMessage The message from the user.
     * @param selectedRoles A list of roles involved in the design task.
     * @return An optional {@link TaskSpec} for the design task, or {@code null} if no design task is needed.
     */
    @Nullable TaskSpec buildDesignTask(String userMessage, List<String> selectedRoles);

    /**
     * Builds a context synchronization task for the selected roles.
     *
     * @param selectedRoles A list of roles that need their context synchronized.
     * @return A {@link TaskSpec} for the context synchronization task.
     */
    TaskSpec buildContextSyncTask(List<String> selectedRoles);

    /**
     * Requests an orchestration plan.
     *
     * @param session The current orchestration session.
     * @param userMessage The message from the user.
     * @param requiresEdits Indicates if the plan requires edits.
     * @param allowedRoles A list of roles allowed in the plan.
     * @param context Additional context for the plan request.
     * @param provider The AI model provider.
     * @param model The AI model to use.
     * @param excludeAdvisory Flag to exclude advisory information from the plan.
     * @param allowEmpty Flag to allow an empty plan.
     * @return An {@link OrchestratorPlan}.
     */
    OrchestratorPlan requestPlan(OrchestrationSession session,
                                 String userMessage,
                                 boolean requiresEdits,
                                 List<String> allowedRoles,
                                 @Nullable String context,
                                 String provider,
                                 String model,
                                 boolean excludeAdvisory,
                                 boolean allowEmpty);

    /**
     * Requests a continuation of an existing orchestration plan.
     *
     * @param session The current orchestration session.
     * @param userMessage The message from the user.
     * @param requiresEdits Indicates if the continuation plan requires edits.
     * @param allowedRoles A list of roles allowed in the plan.
     * @param context Additional context for the plan request.
     * @param errorSummary A summary of any errors encountered.
     * @param plan The existing {@link OrchestratorPlan} to continue from.
     * @param results A list of {@link WorkerResult} from previous steps.
     * @param provider The AI model provider.
     * @param model The AI model to use.
     * @param excludeAdvisory Flag to exclude advisory information from the plan.
     * @param allowEmpty Flag to allow an empty plan.
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
                                             String model,
                                             boolean excludeAdvisory,
                                             boolean allowEmpty);

    /**
     * Sanitizes an orchestration plan based on specified criteria.
     *
     * @param plan The {@link OrchestratorPlan} to sanitize.
     * @param userMessage The message from the user.
     * @param requiresEdits Indicates if the plan requires edits.
     * @param allowedRoles A list of roles allowed in the plan.
     * @param excludeAdvisory Flag to exclude advisory information.
     * @param allowEmpty Flag to allow an empty plan.
     * @return The sanitized {@link OrchestratorPlan}.
     */
    OrchestratorPlan sanitizePlan(OrchestratorPlan plan,
                                  String userMessage,
                                  boolean requiresEdits,
                                  List<String> allowedRoles,
                                  boolean excludeAdvisory,
                                  boolean allowEmpty);

    /**
     * Executes a list of tasks defined in an orchestration plan.
     *
     * @param session The current orchestration session.
     * @param userMessage The message from the user.
     * @param tasks A list of {@link TaskSpec} to execute.
     * @param requiresEdits Indicates if the task execution might require subsequent edits.
     * @param advisoryContext The advisory context for task execution.
     * @param priorResults A list of {@link WorkerResult} from prior tasks.
     * @param provider The AI model provider.
     * @param model The AI model to use.
     * @param taskIndex A map to index tasks by their IDs.
     * @param streamId An optional stream ID for real-time event emission.
     * @return A list of {@link WorkerResult} from the executed tasks.
     */
    List<WorkerResult> executePlanTasks(OrchestrationSession session,
                                        String userMessage,
                                        List<TaskSpec> tasks,
                                        boolean requiresEdits,
                                        String advisoryContext,
                                        List<WorkerResult> priorResults,
                                        String provider,
                                        String model,
                                        Map<String, TaskLog> taskIndex,
                                        @Nullable String streamId);

    /**
     * Executes a list of approved tasks from an orchestration plan.
     *
     * @param session The current orchestration session.
     * @param userMessage The message from the user.
     * @param tasks A list of {@link TaskSpec} to execute.
     * @param requiresEdits Indicates if the task execution might require subsequent edits.
     * @param provider The AI model provider.
     * @param model The AI model to use.
     * @param taskIndex A map to index tasks by their IDs.
     * @param streamId An optional stream ID for real-time event emission.
     * @return A list of {@link WorkerResult} from the executed tasks.
     */
    List<WorkerResult> executeApprovedPlanTasks(OrchestrationSession session,
                                                String userMessage,
                                                List<TaskSpec> tasks,
                                                boolean requiresEdits,
                                                String provider,
                                                String model,
                                                Map<String, TaskLog> taskIndex,
                                                @Nullable String streamId);

    /**
     * Collects failure details from a list of worker results and tasks.
     *
     * @param results A list of {@link WorkerResult} to examine for failures.
     * @param tasks A list of {@link TaskSpec} associated with the results.
     * @return A list of {@link FailureDetail} objects for all identified failures.
     */
    List<FailureDetail> collectFailures(List<WorkerResult> results, List<TaskSpec> tasks);

    /**
     * Builds an error summary from a list of failure details.
     *
     * @param failures A list of {@link FailureDetail}.
     * @return A string summarizing the errors.
     */
    String buildErrorSummary(List<FailureDetail> failures);

    /**
     * Builds a retry plan based on an objective and a list of failures.
     *
     * @param objective The objective for the retry plan.
     * @param failures A list of {@link FailureDetail} that led to the need for a retry.
     * @return An {@link OrchestratorPlan} designed to address the failures.
     */
    OrchestratorPlan buildRetryPlan(String objective, List<FailureDetail> failures);

    /**
     * Constructs an {@link OrchestratorPlan} from an {@link OrchestratorPlanLog}.
     *
     * @param planLog The {@link OrchestratorPlanLog} to convert.
     * @return The reconstructed {@link OrchestratorPlan}.
     */
    OrchestratorPlan planFromLog(OrchestratorPlanLog planLog);

    /**
     * Creates a map of task IDs to {@link TaskLog} objects from an {@link OrchestratorPlanLog}.
     *
     * @param planLog The {@link OrchestratorPlanLog} containing task logs.
     * @return A map where keys are task IDs and values are {@link TaskLog} objects.
     */
    Map<String, TaskLog> taskIndexFromPlanLog(OrchestratorPlanLog planLog);
}
