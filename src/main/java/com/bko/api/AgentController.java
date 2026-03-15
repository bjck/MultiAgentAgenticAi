package com.bko.api;

import com.bko.entity.ScheduledAgent;
import com.bko.entity.ScheduledAgentRun;
import com.bko.entity.PromptLog;
import com.bko.orchestration.service.AgentLibraryService;
import com.bko.orchestration.service.AgentQueryService;
import com.bko.orchestration.service.ScheduledAgentExecutionService;
import com.bko.repository.PromptLogRepository;
import com.bko.repository.ScheduledAgentRepository;
import com.bko.repository.ScheduledAgentRunRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final ScheduledAgentRepository agentRepository;
    private final ScheduledAgentRunRepository runRepository;
    private final PromptLogRepository promptLogRepository;
    private final AgentLibraryService agentLibraryService;
    private final AgentQueryService agentQueryService;
    private final ScheduledAgentExecutionService scheduledAgentExecutionService;

    public AgentController(ScheduledAgentRepository agentRepository,
                           ScheduledAgentRunRepository runRepository,
                           PromptLogRepository promptLogRepository,
                           AgentLibraryService agentLibraryService,
                           AgentQueryService agentQueryService,
                           ScheduledAgentExecutionService scheduledAgentExecutionService) {
        this.agentRepository = agentRepository;
        this.runRepository = runRepository;
        this.promptLogRepository = promptLogRepository;
        this.agentLibraryService = agentLibraryService;
        this.agentQueryService = agentQueryService;
        this.scheduledAgentExecutionService = scheduledAgentExecutionService;
    }

    @GetMapping
    public List<ScheduledAgent> listAgents() {
        return agentRepository.findAll();
    }

    @GetMapping("/{id}")
    public AgentDetailResponse getAgent(@PathVariable UUID id) {
        ScheduledAgent agent = agentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));
        List<ScheduledAgentRun> runs = runRepository.findByAgentOrderByStartedAtDesc(agent);
        return AgentDetailResponse.from(agent, runs, promptLogRepository, this::estimateTokens);
    }

    @PostMapping
    public ScheduledAgent createAgent(@Valid @RequestBody CreateAgentRequest request) {
        return agentLibraryService.createAgent(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAgent(@PathVariable UUID id) {
        if (!agentRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found");
        }
        agentRepository.deleteById(id);
    }

    @PostMapping("/{id}/query")
    public AgentQueryResponse queryAgent(@PathVariable UUID id, @Valid @RequestBody AgentQueryRequest request) {
        return agentQueryService.queryAgent(id, request);
    }

    @PostMapping("/{id}/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void runNow(@PathVariable UUID id) {
        ScheduledAgent agent = agentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));
        scheduledAgentExecutionService.runAgentNow(agent.getId());
    }

    @GetMapping("/{id}/runs/{runId}/events")
    public List<AgentRunEvent> getRunEvents(@PathVariable UUID id, @PathVariable UUID runId) {
        ScheduledAgentRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent run not found"));
        if (run.getAgent() == null || run.getAgent().getId() == null || !run.getAgent().getId().equals(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent run not found");
        }
        if (run.getSessionId() == null) {
            return List.of();
        }
        List<PromptLog> logs = promptLogRepository.findBySessionIdOrderByCreatedAtAsc(run.getSessionId());
        return logs.stream()
                .map(this::toAgentRunEvent)
                .toList();
    }

    public record AgentQueryRequest(
            @NotBlank String query,
            OffsetDateTime from,
            OffsetDateTime to,
            Integer limit
    ) {}

    public record AgentQueryResponse(
            String answer,
            int documentsConsidered
    ) {}

    public record CreateAgentRequest(
            @NotBlank String name,
            String description,
            @NotBlank String objectivePrompt,
            String rawScheduleInput,
            String scheduleExpression,
            Boolean enabled,
            String defaultProvider,
            String defaultModel,
            String tags,
            Long tokenLimitPerRun
    ) {}

    public record UpdateAgentRequest(
            String name,
            String description,
            String objectivePrompt,
            String rawScheduleInput,
            String scheduleExpression,
            Boolean enabled,
            String defaultProvider,
            String defaultModel,
            String tags,
            Long tokenLimitPerRun
    ) {}

    public record RunSummary(
            UUID id,
            OffsetDateTime startedAt,
            OffsetDateTime completedAt,
            ScheduledAgentRun.Status status,
            String errorMessage,
            int eventCount,
            int totalTokens
    ) {}

    public record AgentDetailResponse(
            UUID id,
            String name,
            String description,
            String objectivePrompt,
            String rawScheduleInput,
            String scheduleExpression,
            boolean enabled,
            String defaultProvider,
            String defaultModel,
            String tags,
            OffsetDateTime lastRunAt,
            OffsetDateTime nextRunAt,
            Long tokenLimitPerRun,
            List<RunSummary> runs
    ) {
        static AgentDetailResponse from(ScheduledAgent agent, List<ScheduledAgentRun> runs,
                                      PromptLogRepository promptLogRepository,
                                      java.util.function.ToIntFunction<String> estimateTokens) {
            List<RunSummary> runSummaries = new ArrayList<>();
            List<UUID> sessionIds = runs.stream()
                    .map(ScheduledAgentRun::getSessionId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .toList();
            Map<UUID, int[]> sessionStats = Map.of();
            if (!sessionIds.isEmpty()) {
                List<PromptLog> allLogs = promptLogRepository.findBySession_IdInOrderByCreatedAtAsc(sessionIds);
                sessionStats = allLogs.stream().collect(Collectors.groupingBy(
                        log -> log.getSession().getId(),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                logs -> {
                                    int count = logs.size();
                                    int tokens = logs.stream()
                                            .mapToInt(log -> tokenTotalForLog(log, estimateTokens))
                                            .sum();
                                    return new int[]{count, tokens};
                                }
                        )
                ));
            }
            Map<UUID, int[]> finalSessionStats = sessionStats;
            for (ScheduledAgentRun run : runs) {
                int eventCount = 0;
                int totalTokens = 0;
                if (run.getSessionId() != null && finalSessionStats.containsKey(run.getSessionId())) {
                    int[] stats = finalSessionStats.get(run.getSessionId());
                    eventCount = stats[0];
                    totalTokens = stats[1];
                }
                runSummaries.add(new RunSummary(
                        run.getId(),
                        run.getStartedAt(),
                        run.getCompletedAt(),
                        run.getStatus(),
                        run.getErrorMessage(),
                        eventCount,
                        totalTokens
                ));
            }
            return new AgentDetailResponse(
                    agent.getId(),
                    agent.getName(),
                    agent.getDescription(),
                    agent.getObjectivePrompt(),
                    agent.getRawScheduleInput(),
                    agent.getScheduleExpression(),
                    agent.isEnabled(),
                    agent.getDefaultProvider(),
                    agent.getDefaultModel(),
                    agent.getTags(),
                    agent.getLastRunAt(),
                    agent.getNextRunAt(),
                    agent.getTokenLimitPerRun(),
                    runSummaries
            );
        }

        private static String renderPrompt(String systemPrompt, String userPrompt) {
            StringBuilder sb = new StringBuilder();
            if (StringUtils.hasText(systemPrompt)) {
                sb.append("System:\n").append(systemPrompt.trim());
            }
            if (StringUtils.hasText(userPrompt)) {
                if (!sb.isEmpty()) {
                    sb.append("\n\n");
                }
                sb.append("User:\n").append(userPrompt.trim());
            }
            return sb.toString();
        }

        private static int tokenTotalForLog(PromptLog log, java.util.function.ToIntFunction<String> estimateTokens) {
            if (log.getInputTokenCount() != null && log.getOutputTokenCount() != null) {
                return log.getInputTokenCount() + log.getOutputTokenCount();
            }
            String input = renderPrompt(log.getSystemPrompt(), log.getUserPrompt());
            String output = log.getFullResponse() != null ? log.getFullResponse() : "";
            return estimateTokens.applyAsInt(input) + estimateTokens.applyAsInt(output);
        }
    }

    public record AgentRunEvent(
            OffsetDateTime createdAt,
            String purpose,
            String role,
            String input,
            String output,
            int inputTokens,
            int outputTokens,
            int totalTokens
    ) {}

    private AgentRunEvent toAgentRunEvent(PromptLog log) {
        String input = renderPrompt(log.getSystemPrompt(), log.getUserPrompt());
        String output = log.getFullResponse();
        int inputTokens = log.getInputTokenCount() != null ? log.getInputTokenCount() : estimateTokens(input);
        int outputTokens = log.getOutputTokenCount() != null ? log.getOutputTokenCount() : estimateTokens(output != null ? output : "");
        return new AgentRunEvent(
                log.getCreatedAt(),
                log.getPurpose(),
                log.getRole(),
                input,
                output,
                inputTokens,
                outputTokens,
                inputTokens + outputTokens
        );
    }

    private String renderPrompt(String systemPrompt, String userPrompt) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(systemPrompt)) {
            sb.append("System:\n").append(systemPrompt.trim());
        }
        if (StringUtils.hasText(userPrompt)) {
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append("User:\n").append(userPrompt.trim());
        }
        return sb.toString();
    }

    private int estimateTokens(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        return (int) Math.ceil(text.length() / 4.0);
    }
}
