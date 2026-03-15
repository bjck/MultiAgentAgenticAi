package com.bko.orchestration.service;

import com.bko.entity.ScheduledAgent;
import com.bko.entity.ScheduledAgentRun;
import com.bko.orchestration.OrchestratorService;
import com.bko.orchestration.api.StatePersistenceService;
import com.bko.orchestration.model.OrchestrationResult;
import com.bko.orchestration.model.OrchestratorPlan;
import com.bko.repository.ScheduledAgentRepository;
import com.bko.repository.ScheduledAgentRunRepository;
import com.bko.stream.AgentRunUpdatesHub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduledAgentExecutionServiceTest {

    private ScheduledAgentRepository agentRepository;
    private ScheduledAgentRunRepository runRepository;
    private OrchestratorService orchestratorService;
    private SchedulePatternInterpreter schedulePatternInterpreter;
    private StatePersistenceService persistenceService;
    private AgentRunUpdatesHub agentRunUpdatesHub;
    private ScheduledAgentExecutionService service;

    @BeforeEach
    void setUp() {
        agentRepository = mock(ScheduledAgentRepository.class);
        runRepository = mock(ScheduledAgentRunRepository.class);
        orchestratorService = mock(OrchestratorService.class);
        schedulePatternInterpreter = mock(SchedulePatternInterpreter.class);
        persistenceService = mock(StatePersistenceService.class);
        agentRunUpdatesHub = mock(AgentRunUpdatesHub.class);
        service = new ScheduledAgentExecutionService(
                agentRepository, runRepository, orchestratorService, schedulePatternInterpreter, persistenceService, agentRunUpdatesHub);
    }

    @Test
    void runAgentNowRunsOrchestratorAndUpdatesRunAndAgent() {
        UUID agentId = UUID.randomUUID();
        ScheduledAgent agent = new ScheduledAgent();
        agent.setId(agentId);
        agent.setName("Test");
        agent.setObjectivePrompt("Do something");
        agent.setScheduleExpression("0 0 * * * *");

        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));
        when(runRepository.save(any(ScheduledAgentRun.class))).thenAnswer(inv -> {
            ScheduledAgentRun r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.randomUUID());
            }
            return r;
        });
        when(orchestratorService.orchestrateWithSession(any(), any(), any(), any()))
                .thenReturn(new OrchestrationResult(new OrchestratorPlan("obj", List.of()), List.of(), "Done"));
        when(persistenceService.startSession(any(), any(), any()))
                .thenReturn(new com.bko.entity.OrchestrationSession());
        when(schedulePatternInterpreter.computeInitialNextRun(any(), any()))
                .thenReturn(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));
        when(agentRepository.save(any(ScheduledAgent.class))).thenAnswer(inv -> inv.getArgument(0));

        service.runAgentNow(agentId);

        verify(orchestratorService).orchestrateWithSession(any(), org.mockito.ArgumentMatchers.eq("Do something"), any(), any());
        verify(runRepository, atLeastOnce()).save(any(ScheduledAgentRun.class));
        verify(agentRepository).save(any(ScheduledAgent.class));
    }
}
