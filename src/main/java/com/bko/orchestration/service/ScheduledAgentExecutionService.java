package com.bko.orchestration.service;

import com.bko.entity.ScheduledAgent;
import com.bko.entity.ScheduledAgentRun;
import com.bko.orchestration.OrchestratorService;
import com.bko.orchestration.api.StatePersistenceService;
import com.bko.orchestration.model.OrchestrationResult;
import com.bko.repository.ScheduledAgentRepository;
import com.bko.repository.ScheduledAgentRunRepository;
import com.bko.stream.AgentRunUpdatesHub;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ScheduledAgentExecutionService {

    private final ScheduledAgentRepository agentRepository;
    private final ScheduledAgentRunRepository runRepository;
    private final OrchestratorService orchestratorService;
    private final SchedulePatternInterpreter schedulePatternInterpreter;
    private final StatePersistenceService persistenceService;
    private final AgentRunUpdatesHub agentRunUpdatesHub;

    /**
     * Run the given agent now: run orchestration with its objective prompt,
     * record the run, and update the agent's lastRunAt / nextRunAt.
     * Intended to be called from a background thread (e.g. after returning 202).
     */
    public void runAgentNow(UUID agentId) {
        ScheduledAgent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown agent id: " + agentId));

        ScheduledAgentRun run = new ScheduledAgentRun();
        run.setAgent(agent);
        run.setStatus(ScheduledAgentRun.Status.RUNNING);
        run.setEffectivePrompt(agent.getObjectivePrompt());
        run = runRepository.save(run);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String provider = agent.getDefaultProvider();
        String model = agent.getDefaultModel();
        String normalizedProvider = provider != null && !provider.isBlank() ? provider : null;
        String normalizedModel = model != null && !model.isBlank() ? model : null;
        var session = persistenceService.startSession(agent.getObjectivePrompt(), normalizedProvider, normalizedModel);
        run.setSessionId(session.getId());
        run = runRepository.save(run);
        agentRunUpdatesHub.notifyRunUpdate(agentId);

        try {
            OrchestrationResult result = orchestratorService.orchestrateWithSession(
                    session,
                    agent.getObjectivePrompt(),
                    normalizedProvider,
                    normalizedModel
            );
            run.setStatus(ScheduledAgentRun.Status.SUCCEEDED);
            run.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
            if (result != null && result.finalAnswer() != null && !result.finalAnswer().isBlank()) {
                run.setErrorMessage(null);
            }
        } catch (Exception e) {
            log.warn("Agent run failed: agentId={}", agentId, e);
            run.setStatus(ScheduledAgentRun.Status.FAILED);
            run.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
            run.setErrorMessage(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        } finally {
            runRepository.save(run);
            agent.setLastRunAt(now);
            try {
                OffsetDateTime next = schedulePatternInterpreter.computeInitialNextRun(
                        agent.getScheduleExpression(), OffsetDateTime.now(ZoneOffset.UTC));
                agent.setNextRunAt(next);
            } catch (Exception ex) {
                log.debug("Could not compute next run for agent {}", agentId, ex);
            }
            agentRepository.save(agent);
            agentRunUpdatesHub.notifyRunUpdate(agentId);
        }
    }
}
