package com.bko.orchestration.service;

import static com.bko.orchestration.OrchestrationConstants.*;

import com.bko.config.MultiAgentProperties;
import com.bko.entity.OrchestrationSession;
import com.bko.entity.OrchestratorPlanLog;
import com.bko.entity.TaskLog;
import com.bko.orchestration.api.AgentInvocationService;
import com.bko.orchestration.api.EventProcessingService;
import com.bko.orchestration.api.SkillExecutionService;
import com.bko.orchestration.api.TaskManagementService;
import com.bko.orchestration.model.AdvisoryBundle;
import com.bko.orchestration.model.DiscoveryBundle;
import com.bko.orchestration.model.FailureDetail;
import com.bko.orchestration.model.OrchestratorPlan;
import com.bko.orchestration.model.RoleSelection;
import com.bko.orchestration.model.TaskSpec;
import com.bko.orchestration.model.WorkerResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class TaskManagementServiceImpl implements TaskManagementService {

    private final MultiAgentProperties properties;
    private final FileEditDetectionService fileEditDetectionService;
    private final OrchestrationContextService orchestrationContextService;
    private final AgentInvocationService agentInvocationService;
    private final SkillExecutionService skillExecutionService;
    private final EventProcessingService eventProcessingService;
    private final OrchestrationMetricsService metricsService;

    public TaskManagementServiceImpl(MultiAgentProperties properties,
                                     FileEditDetectionService fileEditDetectionService,
                                     OrchestrationContextService orchestrationContextService,
                                     AgentInvocationService agentInvocationService,
                                     SkillExecutionService skillExecutionService,
                                     EventProcessingService eventProcessingService,
                                     OrchestrationMetricsService metricsService) {
        this.properties = properties;
        this.fileEditDetectionService = fileEditDetectionService;
        this.orchestrationContextService = orchestrationContextService;
        this.agentInvocationService = agentInvocationService;
        this.skillExecutionService = skillExecutionService;
        this.eventProcessingService = eventProcessingService;
        this.metricsService = metricsService;
    }

    @Override
    public List<String> selectRoles(OrchestrationSession session,
                                    String userMessage,
                                    boolean requiresEdits,
                                    @Nullable String context,
                                    String provider,
                                    String model) {
        List<String> availableRoles = normalizedRoles();
        try {
            RoleSelection selection = agentInvocationService.requestRoleSelection(session, userMessage, requiresEdits,
                    availableRoles, context, provider, model);
            List<String> roles = sanitizeSelectedRoles(selection != null ? selection.roles() : null, requiresEdits);
            log.info("Selected roles: {}.", String.join(", ", roles));
            return roles;
        } catch (Exception ex) {
            List<String> roles = sanitizeSelectedRoles(null, requiresEdits);
            log.info("Selected roles (fallback): {}.", String.join(", ", roles));
            return roles;
        }
    }

    @Override
    public DiscoveryBundle runDiscovery(OrchestrationSession session,
                                        String userMessage,
                                        String provider,
                                        String model,
                                        @Nullable String streamId) {
        TaskSpec discoveryTask = buildDiscoveryTask();
        eventProcessingService.emitTaskStart(streamId, discoveryTask);
        WorkerResult result = skillExecutionService.runWorker(session, userMessage, discoveryTask, false, null, provider, model,
                false, true, null, streamId);
        return new DiscoveryBundle(List.of(discoveryTask), List.of(result));
    }

    @Override
    public AdvisoryBundle runAnalysisRounds(OrchestrationSession session,
                                            String userMessage,
                                            List<String> selectedRoles,
                                            boolean requiresEdits,
                                            String provider,
                                            String model,
                                            @Nullable String baseContext,
                                            @Nullable String streamId) {
        if (selectedRoles == null || !selectedRoles.contains(ROLE_ANALYSIS)) {
            return new AdvisoryBundle();
        }
        TaskSpec analysisTask = new TaskSpec(TASK_ID_ANALYSIS, ROLE_ANALYSIS,
                ANALYSIS_TASK_DESCRIPTION, ANALYSIS_TASK_EXPECTED_OUTPUT.formatted(ANALYSIS_HANDOFF_SCHEMA));
        eventProcessingService.emitTaskStart(streamId, analysisTask);
        WorkerResult result = skillExecutionService.runCollaborativeTask(session, userMessage, analysisTask, requiresEdits,
                baseContext, provider, model, null, streamId);
        return new AdvisoryBundle(List.of(analysisTask), List.of(result));
    }

    @Override
    public TaskSpec buildDesignTask(String userMessage, List<String> selectedRoles) {
        if (selectedRoles == null || !selectedRoles.contains(ROLE_DESIGN)) {
            return null;
        }
        return new TaskSpec(TASK_ID_DESIGN, ROLE_DESIGN, DESIGN_TASK_DESCRIPTION,
                DESIGN_TASK_EXPECTED_OUTPUT.formatted(DESIGN_HANDOFF_SCHEMA));
    }

    @Override
    public TaskSpec buildContextSyncTask(List<String> selectedRoles) {
        String role = (selectedRoles != null && selectedRoles.contains(ROLE_ANALYSIS))
                ? ROLE_ANALYSIS
                : ROLE_GENERAL;
        return new TaskSpec(TASK_ID_CONTEXT, role, CONTEXT_SYNC_TASK_DESCRIPTION, CONTEXT_SYNC_TASK_EXPECTED_OUTPUT);
    }

    @Override
    public OrchestratorPlan requestPlan(OrchestrationSession session,
                                        String userMessage,
                                        boolean requiresEdits,
                                        List<String> allowedRoles,
                                        @Nullable String context,
                                        String provider,
                                        String model,
                                        boolean excludeAdvisory,
                                        boolean allowEmpty) {
        OrchestratorPlan plan = agentInvocationService.requestPlan(session, userMessage, requiresEdits,
                allowedRoles, context, provider, model);
        return sanitizePlan(plan, userMessage, requiresEdits, allowedRoles, excludeAdvisory, allowEmpty);
    }

    @Override
    public OrchestratorPlan requestContinuationPlan(OrchestrationSession session,
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
                                                    boolean allowEmpty) {
        OrchestratorPlan continuation = agentInvocationService.requestContinuationPlan(session, userMessage, requiresEdits,
                allowedRoles, context, errorSummary, plan, results, provider, model);
        return sanitizePlan(continuation, userMessage, requiresEdits, allowedRoles, excludeAdvisory, allowEmpty);
    }

    @Override
    public OrchestratorPlan sanitizePlan(OrchestratorPlan plan,
                                         String userMessage,
                                         boolean requiresEdits,
                                         List<String> allowedRoles,
                                         boolean excludeAdvisory,
                                         boolean allowEmpty) {
        if (plan == null || plan.tasks() == null) {
            return allowEmpty ? new OrchestratorPlan(userMessage, List.of())
                    : defaultPlan(userMessage, requiresEdits, allowedRoles);
        }
        String objective = StringUtils.hasText(plan.objective()) ? plan.objective() : userMessage;
        List<TaskSpec> incomingTasks = plan.tasks();
        int maxTasks = Math.min(properties.getMaxTasks(), incomingTasks.size());
        List<TaskSpec> sanitized = new ArrayList<>(maxTasks + 1);
        List<String> normalizedRoles = normalizeAllowedRoles(allowedRoles);
        Set<String> seenSignatures = new LinkedHashSet<>();
        for (int index = 0; index < maxTasks; index++) {
            TaskSpec task = incomingTasks.get(index);
            String role = normalizeRole(task.role(), normalizedRoles);
            if (excludeAdvisory && ADVISORY_ROLES.contains(role)) {
                continue;
            }
            String id = StringUtils.hasText(task.id()) ? task.id() : TASK_PREFIX + (index + 1);
            if (TASK_ID_CONTEXT.equalsIgnoreCase(id) || TASK_ID_DISCOVERY.equalsIgnoreCase(id)) {
                continue;
            }
            String description = StringUtils.hasText(task.description()) ? task.description() : userMessage;
            String expectedOutput = StringUtils.hasText(task.expectedOutput())
                    ? task.expectedOutput()
                    : DEFAULT_EXPECTED_OUTPUT;
            if (requiresEdits) {
                boolean canEdit = ROLE_IMPLEMENTER.equals(role);
                expectedOutput = fileEditDetectionService.appendFileEditInstruction(expectedOutput, canEdit);
            }
            TaskSpec normalizedTask = new TaskSpec(id, role, description, expectedOutput);
            String signature = normalizeTaskSignature(role, description);
            if (seenSignatures.contains(signature)) {
                continue;
            }
            seenSignatures.add(signature);
            sanitized.add(normalizedTask);
        }
        if (requiresEdits && sanitized.stream().noneMatch(task -> ROLE_IMPLEMENTER.equals(task.role()))) {
            sanitized.add(new TaskSpec(TASK_ID_IMPLEMENTATION, ROLE_IMPLEMENTER, userMessage,
                    fileEditDetectionService.appendFileEditInstruction(DEFAULT_IMPLEMENTATION_INSTRUCTION, true)));
        }
        if (sanitized.isEmpty()) {
            return allowEmpty ? new OrchestratorPlan(objective, List.of())
                    : defaultPlan(userMessage, requiresEdits, allowedRoles);
        }
        return new OrchestratorPlan(objective, sanitized);
    }

    @Override
    public List<WorkerResult> executePlanTasks(OrchestrationSession session,
                                               String userMessage,
                                               List<TaskSpec> tasks,
                                               boolean requiresEdits,
                                               String advisoryContext,
                                               List<WorkerResult> priorResults,
                                               String provider,
                                               String model,
                                               Map<String, TaskLog> taskIndex,
                                               @Nullable String streamId) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        if (eventProcessingService.isCancelled(streamId)) {
            return List.of();
        }
        boolean contextAlreadyRun = priorResults != null && priorResults.stream()
                .anyMatch(result -> TASK_ID_CONTEXT.equalsIgnoreCase(result.taskId()));
        List<TaskSpec> effectiveTasks = contextAlreadyRun
                ? tasks.stream().filter(task -> !TASK_ID_CONTEXT.equalsIgnoreCase(task.id())).toList()
                : tasks;
        if (effectiveTasks.isEmpty()) {
            return List.of();
        }
        metricsService.recordTasksExecuted(effectiveTasks.size());
        Duration timeout = properties.getWorkerTimeout();
        if (requiresEdits) {
            List<TaskSpec> engineeringTasks = effectiveTasks.stream()
                    .filter(task -> ROLE_ENGINEERING.equals(task.role()))
                    .toList();
            List<TaskSpec> implementerTasks = effectiveTasks.stream()
                    .filter(task -> ROLE_IMPLEMENTER.equals(task.role()))
                    .toList();
            List<TaskSpec> otherTasks = effectiveTasks.stream()
                    .filter(task -> !ROLE_ENGINEERING.equals(task.role()) && !ROLE_IMPLEMENTER.equals(task.role()))
                    .toList();
            List<WorkerResult> results = new ArrayList<>(effectiveTasks.size());
            String sharedContext = orchestrationContextService.buildResultsContext(priorResults);
            String engineeringContext = orchestrationContextService.buildResultsContext(
                    orchestrationContextService.filterResultsByRole(priorResults, Set.of(ROLE_ENGINEERING)));
            for (TaskSpec task : engineeringTasks) {
                if (eventProcessingService.isCancelled(streamId)) {
                    return results;
                }
                String taskContext = engineeringContext;
                TaskLog taskLog = taskIndex.get(task.id());
                eventProcessingService.emitTaskStart(streamId, task);
                WorkerResult result = CompletableFuture
                        .supplyAsync(() -> skillExecutionService.runCollaborativeTask(session, userMessage, task, true, taskContext, provider, model, taskLog, streamId))
                        .orTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                        .exceptionally(ex -> {
                            WorkerResult failed = new WorkerResult(task.id(), task.role(),
                                    WORKER_FAILED_MESSAGE + ex.getMessage());
                            eventProcessingService.emitTaskOutput(streamId, failed);
                            eventProcessingService.emitTaskComplete(streamId, failed);
                            return failed;
                        })
                        .join();
                results.add(result);
                engineeringContext = orchestrationContextService.mergeContexts(engineeringContext,
                        orchestrationContextService.buildResultsContext(List.of(result)));
            }
            if (!otherTasks.isEmpty()) {
                if (eventProcessingService.isCancelled(streamId)) {
                    return results;
                }
                String discussionContext = orchestrationContextService.mergeContexts(sharedContext,
                        orchestrationContextService.buildResultsContext(results));
                List<CompletableFuture<WorkerResult>> futures = otherTasks.stream()
                        .map(task -> {
                            TaskLog tl = taskIndex.get(task.id());
                            eventProcessingService.emitTaskStart(streamId, task);
                            return CompletableFuture.supplyAsync(() -> skillExecutionService.runCollaborativeTask(session, userMessage, task, true,
                                            discussionContext, provider, model, tl, streamId))
                                    .orTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                                    .exceptionally(ex -> {
                                        WorkerResult failed = new WorkerResult(task.id(), task.role(),
                                                WORKER_FAILED_MESSAGE + ex.getMessage());
                                        eventProcessingService.emitTaskOutput(streamId, failed);
                                        eventProcessingService.emitTaskComplete(streamId, failed);
                                        return failed;
                                    });
                        })
                        .toList();
                if (eventProcessingService.isCancelled(streamId)) {
                    futures.forEach(future -> future.cancel(true));
                    return results;
                }
                results.addAll(futures.stream().map(CompletableFuture::join).toList());
            }
            if (implementerTasks.isEmpty()) {
                return results;
            }
            String implementationContext = orchestrationContextService.mergeContexts(sharedContext,
                    orchestrationContextService.buildResultsContext(results));
            for (TaskSpec task : implementerTasks) {
                if (eventProcessingService.isCancelled(streamId)) {
                    return results;
                }
                String taskContext = implementationContext;
                TaskLog taskLog = taskIndex.get(task.id());
                eventProcessingService.emitTaskStart(streamId, task);
                WorkerResult result = CompletableFuture
                        .supplyAsync(() -> skillExecutionService.runCollaborativeTask(session, userMessage, task, true, taskContext, provider, model, taskLog, streamId))
                        .orTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                        .exceptionally(ex -> {
                            WorkerResult failed = new WorkerResult(task.id(), task.role(),
                                    WORKER_FAILED_MESSAGE + ex.getMessage());
                            eventProcessingService.emitTaskOutput(streamId, failed);
                            eventProcessingService.emitTaskComplete(streamId, failed);
                            return failed;
                        })
                        .join();
                results.add(result);
                implementationContext = orchestrationContextService.mergeContexts(implementationContext,
                        orchestrationContextService.buildResultsContext(List.of(result)));
            }
            return results;
        }

        String context = orchestrationContextService.buildResultsContext(priorResults);
        List<CompletableFuture<WorkerResult>> futures = effectiveTasks.stream()
                .map(task -> {
                    TaskLog tl = taskIndex.get(task.id());
                    eventProcessingService.emitTaskStart(streamId, task);
                    return CompletableFuture.supplyAsync(() -> skillExecutionService.runCollaborativeTask(session, userMessage, task, false,
                                    context, provider, model, tl, streamId))
                            .orTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                            .exceptionally(ex -> {
                                WorkerResult failed = new WorkerResult(task.id(), task.role(),
                                        WORKER_FAILED_MESSAGE + ex.getMessage());
                                eventProcessingService.emitTaskOutput(streamId, failed);
                                eventProcessingService.emitTaskComplete(streamId, failed);
                                return failed;
                            });
                })
                .toList();
        if (eventProcessingService.isCancelled(streamId)) {
            futures.forEach(future -> future.cancel(true));
            return List.of();
        }
        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    @Override
    public List<WorkerResult> executeApprovedPlanTasks(OrchestrationSession session,
                                                       String userMessage,
                                                       List<TaskSpec> tasks,
                                                       boolean requiresEdits,
                                                       String provider,
                                                       String model,
                                                       Map<String, TaskLog> taskIndex,
                                                       @Nullable String streamId) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        if (eventProcessingService.isCancelled(streamId)) {
            return List.of();
        }
        List<TaskSpec> effectiveTasks = tasks.stream()
                .filter(task -> !TASK_ID_CONTEXT.equalsIgnoreCase(task.id()))
                .filter(task -> !TASK_ID_DISCOVERY.equalsIgnoreCase(task.id()))
                .toList();
        if (effectiveTasks.isEmpty()) {
            return List.of();
        }
        metricsService.recordApprovedTasksExecuted(effectiveTasks.size());
        Duration timeout = properties.getWorkerTimeout();

        List<TaskSpec> implementerTasks = effectiveTasks.stream()
                .filter(task -> ROLE_IMPLEMENTER.equals(task.role()))
                .toList();
        List<TaskSpec> advisoryTasks = effectiveTasks.stream()
                .filter(task -> !ROLE_IMPLEMENTER.equals(task.role()))
                .toList();

        List<WorkerResult> results = new ArrayList<>(effectiveTasks.size());
        if (!advisoryTasks.isEmpty()) {
            if (eventProcessingService.isCancelled(streamId)) {
                return results;
            }
            List<CompletableFuture<WorkerResult>> futures = advisoryTasks.stream()
                    .map(task -> {
                        TaskLog tl = taskIndex.get(task.id());
                        boolean taskRequiresEdits = false;
                        eventProcessingService.emitTaskStart(streamId, task);
                        return CompletableFuture.supplyAsync(
                                        () -> skillExecutionService.runWorker(session, userMessage, task, taskRequiresEdits, null, provider, model,
                                                false, false, tl, streamId))
                                .orTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                                .exceptionally(ex -> {
                                    WorkerResult failed = new WorkerResult(task.id(), task.role(),
                                            WORKER_FAILED_MESSAGE + ex.getMessage());
                                    eventProcessingService.emitTaskOutput(streamId, failed);
                                    eventProcessingService.emitTaskComplete(streamId, failed);
                                    return failed;
                                });
                    })
                    .toList();
            if (eventProcessingService.isCancelled(streamId)) {
                futures.forEach(future -> future.cancel(true));
                return results;
            }
            results.addAll(futures.stream().map(CompletableFuture::join).toList());
        }

        if (!implementerTasks.isEmpty()) {
            if (eventProcessingService.isCancelled(streamId)) {
                return results;
            }
            String implementationContext = orchestrationContextService.buildResultsContext(results);
            for (TaskSpec task : implementerTasks) {
                if (eventProcessingService.isCancelled(streamId)) {
                    return results;
                }
                TaskLog taskLog = taskIndex.get(task.id());
                String taskContext = implementationContext;
                boolean taskRequiresEdits = requiresEdits;
                eventProcessingService.emitTaskStart(streamId, task);
                WorkerResult result = CompletableFuture
                        .supplyAsync(() -> skillExecutionService.runWorker(session, userMessage, task, taskRequiresEdits, taskContext, provider, model,
                                false, false, taskLog, streamId))
                        .orTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                        .exceptionally(ex -> {
                            WorkerResult failed = new WorkerResult(task.id(), task.role(),
                                    WORKER_FAILED_MESSAGE + ex.getMessage());
                            eventProcessingService.emitTaskOutput(streamId, failed);
                            eventProcessingService.emitTaskComplete(streamId, failed);
                            return failed;
                        })
                        .join();
                results.add(result);
                implementationContext = orchestrationContextService.mergeContexts(implementationContext,
                        orchestrationContextService.buildResultsContext(List.of(result)));
            }
        }
        return results;
    }

    @Override
    public List<FailureDetail> collectFailures(List<WorkerResult> results, List<TaskSpec> tasks) {
        if (results == null || results.isEmpty() || tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        Map<String, TaskSpec> taskIndex = new HashMap<>();
        for (TaskSpec task : tasks) {
            taskIndex.put(task.id(), task);
        }
        List<FailureDetail> failures = new ArrayList<>();
        for (WorkerResult result : results) {
            if (!isFailureOutput(result.output())) {
                continue;
            }
            TaskSpec task = taskIndex.get(result.taskId());
            String reason = extractFailureReason(result.output());
            if (task != null) {
                failures.add(new FailureDetail(task, reason));
            }
        }
        return failures;
    }

    @Override
    public String buildErrorSummary(List<FailureDetail> failures) {
        if (failures == null || failures.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (FailureDetail failure : failures) {
            sb.append("- [").append(failure.task().role()).append(" ")
                    .append(failure.task().id()).append("] ")
                    .append(failure.reason()).append("\n");
        }
        return sb.toString().trim();
    }

    @Override
    public OrchestratorPlan buildRetryPlan(String objective, List<FailureDetail> failures) {
        List<TaskSpec> tasks = new ArrayList<>();
        for (FailureDetail failure : failures) {
            TaskSpec task = failure.task();
            String description = task.description();
            if (StringUtils.hasText(failure.reason())) {
                description = description + " (Retry and resolve error: " + failure.reason() + ")";
            }
            tasks.add(new TaskSpec(task.id(), task.role(), description, task.expectedOutput()));
        }
        return new OrchestratorPlan(objective, tasks);
    }

    @Override
    public OrchestratorPlan planFromLog(OrchestratorPlanLog planLog) {
        String objective = StringUtils.hasText(planLog.getObjective())
                ? planLog.getObjective()
                : (planLog.getSession() != null ? planLog.getSession().getUserPrompt() : "");
        List<TaskLog> tasks = planLog.getTasks() == null ? List.of() : planLog.getTasks();
        List<TaskLog> ordered = tasks.stream()
                .sorted(java.util.Comparator.comparing(TaskLog::getCreatedAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .toList();
        List<TaskSpec> specs = new ArrayList<>(ordered.size());
        int index = 1;
        for (TaskLog taskLog : ordered) {
            String id = StringUtils.hasText(taskLog.getTaskIdAlias()) ? taskLog.getTaskIdAlias() : TASK_PREFIX + index;
            String role = StringUtils.hasText(taskLog.getRole()) ? taskLog.getRole() : ROLE_GENERAL;
            String description = StringUtils.hasText(taskLog.getDescription()) ? taskLog.getDescription() : objective;
            String expectedOutput = StringUtils.hasText(taskLog.getExpectedOutput())
                    ? taskLog.getExpectedOutput()
                    : DEFAULT_EXPECTED_OUTPUT;
            specs.add(new TaskSpec(id, role, description, expectedOutput));
            index++;
        }
        return new OrchestratorPlan(objective, specs);
    }

    @Override
    public Map<String, TaskLog> taskIndexFromPlanLog(OrchestratorPlanLog planLog) {
        if (planLog.getTasks() == null || planLog.getTasks().isEmpty()) {
            return Map.of();
        }
        Map<String, TaskLog> index = new HashMap<>();
        int counter = 1;
        for (TaskLog taskLog : planLog.getTasks()) {
            String id = StringUtils.hasText(taskLog.getTaskIdAlias()) ? taskLog.getTaskIdAlias() : TASK_PREFIX + counter;
            index.put(id, taskLog);
            counter++;
        }
        return index;
    }

    private TaskSpec buildDiscoveryTask() {
        List<String> roles = normalizedRoles();
        String role = roles.contains(ROLE_ANALYSIS) ? ROLE_ANALYSIS : fallbackRole(roles);
        return new TaskSpec(TASK_ID_DISCOVERY, role, DISCOVERY_TASK_DESCRIPTION, DISCOVERY_TASK_EXPECTED_OUTPUT);
    }

    private String normalizeTaskSignature(String role, String description) {
        String normalizedRole = StringUtils.hasText(role) ? role.trim().toLowerCase(Locale.ROOT) : "";
        String normalizedDescription = StringUtils.hasText(description)
                ? description.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT)
                : "";
        return normalizedRole + "::" + normalizedDescription;
    }

    private OrchestratorPlan defaultPlan(String userMessage, boolean requiresEdits, List<String> allowedRoles) {
        List<String> normalizedRoles = normalizeAllowedRoles(allowedRoles);
        String role = requiresEdits
                ? (normalizedRoles.contains(ROLE_IMPLEMENTER)
                        ? ROLE_IMPLEMENTER
                        : (normalizedRoles.contains(ROLE_ENGINEERING) ? ROLE_ENGINEERING : fallbackRole(normalizedRoles)))
                : fallbackRole(normalizedRoles);
        String expectedOutput = DEFAULT_COMPLETE_RESPONSE_INSTRUCTION;
        if (requiresEdits) {
            expectedOutput = fileEditDetectionService.appendFileEditInstruction(expectedOutput, ROLE_IMPLEMENTER.equals(role));
        }
        TaskSpec fallback = new TaskSpec(TASK_ID_FALLBACK, role, userMessage, expectedOutput);
        return new OrchestratorPlan(userMessage, List.of(fallback));
    }

    private String normalizeRole(String role, List<String> allowedRoles) {
        if (!StringUtils.hasText(role)) {
            return fallbackRole(allowedRoles);
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        return allowedRoles.contains(normalized) ? normalized : fallbackRole(allowedRoles);
    }

    private List<String> normalizedRoles() {
        return properties.getWorkerRoles().stream()
                .filter(StringUtils::hasText)
                .map(role -> role.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private List<String> normalizeAllowedRoles(List<String> allowedRoles) {
        List<String> normalized = allowedRoles == null ? List.of() : allowedRoles.stream()
                .filter(StringUtils::hasText)
                .map(role -> role.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            return normalizedRoles();
        }
        return normalized;
    }

    private List<String> sanitizeSelectedRoles(List<String> selected, boolean requiresEdits) {
        List<String> available = normalizedRoles();
        LinkedHashSet<String> roles = new LinkedHashSet<>();
        if (selected != null) {
            for (String role : selected) {
                if (!StringUtils.hasText(role)) {
                    continue;
                }
                String normalized = role.trim().toLowerCase(Locale.ROOT);
                if (available.contains(normalized)) {
                    roles.add(normalized);
                }
            }
        }
        if (roles.isEmpty()) {
            roles.addAll(available);
        }
        if (requiresEdits) {
            if (!roles.contains(ROLE_ENGINEERING) && available.contains(ROLE_ENGINEERING)) {
                roles.add(ROLE_ENGINEERING);
            }
            if (!roles.contains(ROLE_IMPLEMENTER)) {
                if (available.contains(ROLE_IMPLEMENTER)) {
                    roles.add(ROLE_IMPLEMENTER);
                } else if (available.contains(ROLE_ENGINEERING)) {
                    roles.add(ROLE_ENGINEERING);
                } else {
                    roles.add(fallbackRole(available));
                }
            }
        }
        return new ArrayList<>(roles);
    }

    private String fallbackRole(List<String> allowedRoles) {
        if (allowedRoles.contains(ROLE_GENERAL)) {
            return ROLE_GENERAL;
        }
        return allowedRoles.isEmpty() ? ROLE_GENERAL : allowedRoles.getFirst();
    }

    private boolean isFailureOutput(@Nullable String output) {
        if (!StringUtils.hasText(output)) {
            return false;
        }
        String normalized = output.toLowerCase(Locale.ROOT);
        return normalized.startsWith(WORKER_FAILED_MESSAGE.toLowerCase(Locale.ROOT))
                || normalized.contains("tool error:");
    }

    private String extractFailureReason(String output) {
        if (!StringUtils.hasText(output)) {
            return "";
        }
        String trimmed = output.trim();
        if (trimmed.startsWith(WORKER_FAILED_MESSAGE)) {
            return trimmed.substring(WORKER_FAILED_MESSAGE.length()).trim();
        }
        return trimmed;
    }
}
