package com.bko.orchestration.service;

import com.bko.BaseIntegrationTest;
import com.bko.entity.OrchestrationSession;
import com.bko.entity.ScheduledAgent;
import com.bko.entity.ScheduledAgentRun;
import com.bko.orchestration.OrchestratorService;
import com.bko.orchestration.api.StatePersistenceService;
import com.bko.orchestration.model.OrchestrationResult;
import com.bko.orchestration.model.OrchestratorPlan;
import com.bko.repository.ScheduledAgentRepository;
import com.bko.repository.ScheduledAgentRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * Integration test for {@link ScheduledAgentTriggerService}: full Spring context, Testcontainers
 * Postgres, and Liquibase. Requires Docker to run; skipped when Docker is unavailable.
 */
@SpringBootTest
@TestPropertySource(properties = "LIQUIBASE_ENABLED=true")
@DirtiesContext
@EnabledIfDockerAvailable
class ScheduledAgentTriggerServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ScheduledAgentTriggerService triggerService;

    @Autowired
    private ScheduledAgentRepository agentRepository;

    @Autowired
    private ScheduledAgentRunRepository runRepository;

    @MockitoBean
    private OrchestratorService orchestratorService;

    @MockitoBean
    private StatePersistenceService persistenceService;

    @MockitoBean(name = "orchestrationExecutor")
    private ExecutorService orchestrationExecutor;

    @Test
    void triggerDueAgentsRunsEnabledAgentWithNextRunInPastAndUpdatesNextRun() {
        // Run submitted tasks synchronously so execution completes before we assert
        doAnswer(inv -> {
            inv.getArgument(0, Runnable.class).run();
            return null;
        }).when(orchestrationExecutor).execute(any(Runnable.class));

        OrchestrationSession session = new OrchestrationSession();
        session.setId(UUID.randomUUID());
        session.setUserPrompt("Report status");
        when(persistenceService.startSession(any(), any(), any())).thenReturn(session);
        when(orchestratorService.orchestrateWithSession(any(), any(), any(), any()))
                .thenReturn(new OrchestrationResult(new OrchestratorPlan("Report", List.of()), List.of(), "Done"));

        OffsetDateTime past = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5);
        ScheduledAgent agent = new ScheduledAgent();
        agent.setName("Hourly reporter");
        agent.setObjectivePrompt("Report status every hour");
        agent.setScheduleExpression("0 0 * * * *");
        agent.setEnabled(true);
        agent.setNextRunAt(past);
        agent = agentRepository.save(agent);

        triggerService.triggerDueAgents();

        List<ScheduledAgentRun> runs = runRepository.findByAgentOrderByStartedAtDesc(agent);
        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).getStatus()).isEqualTo(ScheduledAgentRun.Status.SUCCEEDED);
        assertThat(runs.get(0).getEffectivePrompt()).isEqualTo("Report status every hour");

        ScheduledAgent updated = agentRepository.findById(agent.getId()).orElseThrow();
        assertThat(updated.getLastRunAt()).isNotNull();
        assertThat(updated.getNextRunAt()).isAfter(past);
    }
}
