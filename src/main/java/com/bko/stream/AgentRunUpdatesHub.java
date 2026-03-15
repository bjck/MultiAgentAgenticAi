package com.bko.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Broadcasts agent-run lifecycle events to frontends viewing a given agent.
 * When a scheduled agent run is created or updated, subscribers for that agent
 * receive a push so they can refresh the run list without polling.
 */
@Component
public class AgentRunUpdatesHub {

    private static final Logger log = LoggerFactory.getLogger(AgentRunUpdatesHub.class);
    private static final String EVENT_TYPE = "agent-run-update";

    private final ObjectMapper objectMapper;
    /** agentId (string) -> set of WebSocketSession */
    private final Map<String, Set<WebSocketSession>> sessionsByAgentId = new ConcurrentHashMap<>();

    public AgentRunUpdatesHub(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void registerSession(UUID agentId, WebSocketSession session) {
        String key = agentId.toString();
        sessionsByAgentId.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(session);
        session.getAttributes().put("agentId", key);
    }

    public void removeSession(WebSocketSession session) {
        Object keyObj = session.getAttributes().get("agentId");
        if (keyObj == null) {
            return;
        }
        String key = keyObj.toString();
        Set<WebSocketSession> set = sessionsByAgentId.get(key);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) {
                sessionsByAgentId.remove(key);
            }
        }
    }

    /**
     * Notify all subscribers for this agent that runs have changed (new run or status update).
     * Call this after creating or updating a ScheduledAgentRun.
     */
    public void notifyRunUpdate(UUID agentId) {
        String key = agentId.toString();
        Set<WebSocketSession> set = sessionsByAgentId.get(key);
        if (set == null || set.isEmpty()) {
            return;
        }
        Map<String, Object> payload = Map.of(
                "type", EVENT_TYPE,
                "agentId", key
        );
        try {
            String json = objectMapper.writeValueAsString(payload);
            TextMessage message = new TextMessage(json);
            set.forEach(session -> send(session, message));
        } catch (IOException e) {
            log.warn("Failed to serialize agent-run-update payload", e);
        }
    }

    private void send(WebSocketSession session, TextMessage message) {
        if (!session.isOpen()) {
            return;
        }
        try {
            synchronized (session) {
                session.sendMessage(message);
            }
        } catch (IOException e) {
            log.debug("Failed to send agent-run-update: {}", e.getMessage());
        }
    }
}
