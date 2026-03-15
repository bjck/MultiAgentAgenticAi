package com.bko.orchestration.service;

import com.bko.api.AgentController.CreateAgentRequest;
import com.bko.api.AgentController.UpdateAgentRequest;
import com.bko.entity.ScheduledAgent;
import com.bko.repository.ScheduledAgentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentLibraryServiceTest {

    private ScheduledAgentRepository agentRepository;
    private SchedulePatternInterpreter schedulePatternInterpreter;
    private AgentLibraryService service;

    @BeforeEach
    void setUp() {
        agentRepository = mock(ScheduledAgentRepository.class);
        schedulePatternInterpreter = mock(SchedulePatternInterpreter.class);
        service = new AgentLibraryService(agentRepository, schedulePatternInterpreter);
    }

    @Test
    void createAgentComputesScheduleExpressionAndNextRun() {
        CreateAgentRequest request = new CreateAgentRequest(
                "Arxiv Agent",
                "Fetch latest software engineering papers",
                "Fetch latest arXiv cs.SE papers and store abstracts.",
                "every hour",
                null,
                true,
                "google",
                "gemini-2.5-flash",
                null,
                null
        );
        when(schedulePatternInterpreter.interpret("every hour")).thenReturn("0 0 * * * *");
        OffsetDateTime next = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
        when(schedulePatternInterpreter.computeInitialNextRun(any(), any())).thenReturn(next);

        when(agentRepository.save(any(ScheduledAgent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ScheduledAgent saved = service.createAgent(request);

        assertEquals("Arxiv Agent", saved.getName());
        assertEquals("0 0 * * * *", saved.getScheduleExpression());
        assertEquals(next, saved.getNextRunAt());
        assertTrue(saved.isEnabled());
    }

    @Test
    void updateAgentAllowsChangingScheduleAndObjective() {
        UUID id = UUID.randomUUID();
        ScheduledAgent existing = new ScheduledAgent();
        existing.setId(id);
        existing.setName("Existing");
        existing.setScheduleExpression("0 0 * * * *");
        when(agentRepository.findById(id)).thenReturn(Optional.of(existing));
        when(agentRepository.save(any(ScheduledAgent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateAgentRequest request = new UpdateAgentRequest(
                "Updated",
                null,
                "New objective",
                "daily",
                null,
                null,
                null,
                null,
                null,
                null
        );

        when(schedulePatternInterpreter.interpret("daily")).thenReturn("0 0 0 * * *");
        OffsetDateTime next = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1);
        when(schedulePatternInterpreter.computeInitialNextRun(any(), any())).thenReturn(next);

        ScheduledAgent result = service.updateAgent(id, request);

        assertEquals("Updated", result.getName());
        assertEquals("New objective", result.getObjectivePrompt());
        assertEquals("0 0 0 * * *", result.getScheduleExpression());
        assertEquals(next, result.getNextRunAt());
    }

    @Test
    void setEnabledInitializesNextRunWhenEnabling() {
        UUID id = UUID.randomUUID();
        ScheduledAgent existing = new ScheduledAgent();
        existing.setId(id);
        existing.setScheduleExpression("0 0 * * * *");
        existing.setEnabled(false);
        when(agentRepository.findById(id)).thenReturn(Optional.of(existing));
        when(agentRepository.save(any(ScheduledAgent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OffsetDateTime next = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
        when(schedulePatternInterpreter.computeInitialNextRun(any(), any())).thenReturn(next);

        ScheduledAgent result = service.setEnabled(id, true);

        assertTrue(result.isEnabled());
        assertEquals(next, result.getNextRunAt());
    }
}

