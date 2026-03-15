package com.bko.orchestration.service;

import com.bko.entity.ScheduledAgent;
import com.bko.repository.ScheduledAgentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduledAgentTriggerServiceTest {

    private ScheduledAgentRepository agentRepository;
    private ScheduledAgentExecutionService scheduledAgentExecutionService;
    private ScheduledAgentTriggerService service;

    @BeforeEach
    void setUp() {
        agentRepository = mock(ScheduledAgentRepository.class);
        scheduledAgentExecutionService = mock(ScheduledAgentExecutionService.class);
        service = new ScheduledAgentTriggerService(agentRepository, scheduledAgentExecutionService);
    }

    @Test
    void triggerDueAgentsDoesNothingWhenNoAgentsDue() {
        when(agentRepository.findByEnabledIsTrueAndNextRunAtLessThanEqual(any(OffsetDateTime.class))).thenReturn(List.of());

        service.triggerDueAgents();

        verify(agentRepository).findByEnabledIsTrueAndNextRunAtLessThanEqual(any(OffsetDateTime.class));
        verify(scheduledAgentExecutionService, never()).runAgentNow(any());
    }

    @Test
    void triggerDueAgentsTriggersEachDueAgent() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        ScheduledAgent agent1 = new ScheduledAgent();
        agent1.setId(id1);
        agent1.setName("Agent 1");
        agent1.setNextRunAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        ScheduledAgent agent2 = new ScheduledAgent();
        agent2.setId(id2);
        agent2.setName("Agent 2");
        agent2.setNextRunAt(OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(30));

        when(agentRepository.findByEnabledIsTrueAndNextRunAtLessThanEqual(any()))
                .thenReturn(List.of(agent1, agent2));

        service.triggerDueAgents();

        verify(scheduledAgentExecutionService).runAgentNow(eq(id1));
        verify(scheduledAgentExecutionService).runAgentNow(eq(id2));
    }

    @Test
    void triggerDueAgentsTriggersSingleDueAgent() {
        UUID id = UUID.randomUUID();
        ScheduledAgent agent = new ScheduledAgent();
        agent.setId(id);
        agent.setName("Hourly reporter");
        agent.setNextRunAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));

        when(agentRepository.findByEnabledIsTrueAndNextRunAtLessThanEqual(any()))
                .thenReturn(List.of(agent));

        service.triggerDueAgents();

        verify(scheduledAgentExecutionService).runAgentNow(eq(id));
    }
}
