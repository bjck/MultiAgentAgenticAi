package com.bko.orchestration.service;

import com.bko.api.AgentController.CreateAgentRequest;
import com.bko.api.AgentController.UpdateAgentRequest;
import com.bko.entity.ScheduledAgent;
import com.bko.repository.ScheduledAgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentLibraryService {
    private final ScheduledAgentRepository agentRepository;
    private final SchedulePatternInterpreter schedulePatternInterpreter;

    @Transactional
    public ScheduledAgent createAgent(CreateAgentRequest request) {
        ScheduledAgent agent = new ScheduledAgent();
        agent.setName(request.name());
        agent.setDescription(request.description());
        agent.setObjectivePrompt(request.objectivePrompt());
        agent.setRawScheduleInput(request.rawScheduleInput());
        String expression = resolveScheduleExpression(request.scheduleExpression(), request.rawScheduleInput());
        agent.setScheduleExpression(expression);
        agent.setEnabled(request.enabled() == null || Boolean.TRUE.equals(request.enabled()));
        agent.setDefaultProvider(request.defaultProvider());
        agent.setDefaultModel(request.defaultModel());
        agent.setTags(request.tags());
        agent.setTokenLimitPerRun(request.tokenLimitPerRun());
        // Initialize nextRunAt on creation
        agent.setNextRunAt(schedulePatternInterpreter.computeInitialNextRun(expression, OffsetDateTime.now(ZoneOffset.UTC)));
        return agentRepository.save(agent);
    }

    @Transactional
    public ScheduledAgent updateAgent(UUID id, UpdateAgentRequest request) {
        ScheduledAgent agent = agentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown agent id: " + id));
        if (StringUtils.hasText(request.name())) {
            agent.setName(request.name());
        }
        if (request.description() != null) {
            agent.setDescription(request.description());
        }
        if (StringUtils.hasText(request.objectivePrompt())) {
            agent.setObjectivePrompt(request.objectivePrompt());
        }
        if (request.rawScheduleInput() != null) {
            agent.setRawScheduleInput(request.rawScheduleInput());
        }
        if (request.scheduleExpression() != null || request.rawScheduleInput() != null) {
            String expression = resolveScheduleExpression(request.scheduleExpression(), request.rawScheduleInput());
            agent.setScheduleExpression(expression);
            agent.setNextRunAt(schedulePatternInterpreter.computeInitialNextRun(expression, OffsetDateTime.now(ZoneOffset.UTC)));
        }
        if (request.enabled() != null) {
            agent.setEnabled(request.enabled());
        }
        if (request.defaultProvider() != null) {
            agent.setDefaultProvider(request.defaultProvider());
        }
        if (request.defaultModel() != null) {
            agent.setDefaultModel(request.defaultModel());
        }
        if (request.tags() != null) {
            agent.setTags(request.tags());
        }
        if (request.tokenLimitPerRun() != null) {
            agent.setTokenLimitPerRun(request.tokenLimitPerRun());
        }
        return agentRepository.save(agent);
    }

    @Transactional
    public ScheduledAgent setEnabled(UUID id, boolean enabled) {
        ScheduledAgent agent = agentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown agent id: " + id));
        agent.setEnabled(enabled);
        if (enabled && agent.getNextRunAt() == null) {
            agent.setNextRunAt(schedulePatternInterpreter.computeInitialNextRun(
                    agent.getScheduleExpression(), OffsetDateTime.now(ZoneOffset.UTC)));
        }
        return agentRepository.save(agent);
    }

    private String resolveScheduleExpression(String explicitExpression, String rawInput) {
        if (StringUtils.hasText(explicitExpression)) {
            return explicitExpression.trim();
        }
        if (StringUtils.hasText(rawInput)) {
            return schedulePatternInterpreter.interpret(rawInput.trim());
        }
        throw new IllegalArgumentException("Either scheduleExpression or rawScheduleInput must be provided.");
    }
}
