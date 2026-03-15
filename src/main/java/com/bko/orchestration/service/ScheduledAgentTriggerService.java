package com.bko.orchestration.service;

import com.bko.entity.ScheduledAgent;
import com.bko.repository.ScheduledAgentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Polls for scheduled agents that are due and triggers their execution.
 * Runs on the scheduler thread so planning and execution stay on the same thread (Spring AI advisor chain).
 */
@Service
@Slf4j
public class ScheduledAgentTriggerService {

    private final ScheduledAgentRepository agentRepository;
    private final ScheduledAgentExecutionService scheduledAgentExecutionService;

    public ScheduledAgentTriggerService(ScheduledAgentRepository agentRepository,
                                        ScheduledAgentExecutionService scheduledAgentExecutionService) {
        this.agentRepository = agentRepository;
        this.scheduledAgentExecutionService = scheduledAgentExecutionService;
    }

    @Scheduled(fixedDelayString = "${multiagent.scheduled-agent-poll-interval:60000}")
    public void triggerDueAgents() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<ScheduledAgent> due = agentRepository.findByEnabledIsTrueAndNextRunAtLessThanEqual(now);
        if (due.isEmpty()) {
            return;
        }
        for (ScheduledAgent agent : due) {
            UUID agentId = agent.getId();
            log.debug("Triggering scheduled agent id={} name='{}' (nextRunAt={})", agentId, agent.getName(), agent.getNextRunAt());
            scheduledAgentExecutionService.runAgentNow(agentId);
        }
    }
}
