package com.bko.stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class AgentRunUpdatesWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AgentRunUpdatesWebSocketHandler.class);

    private final AgentRunUpdatesHub hub;

    public AgentRunUpdatesWebSocketHandler(AgentRunUpdatesHub hub) {
        this.hub = hub;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            try {
                session.close(CloseStatus.BAD_DATA);
            } catch (IOException e) {
                log.debug("Error closing session", e);
            }
            return;
        }
        Map<String, String> queryParams = parseQueryParams(uri.getQuery());
        String agentIdParam = queryParams.get("agentId");
        if (agentIdParam == null || agentIdParam.isBlank()) {
            try {
                session.close(CloseStatus.BAD_DATA);
            } catch (IOException e) {
                log.debug("Error closing session", e);
            }
            return;
        }
        try {
            UUID agentId = UUID.fromString(agentIdParam.trim());
            hub.registerSession(agentId, session);
        } catch (IllegalArgumentException e) {
            log.debug("Invalid agentId in WebSocket query: {}", agentIdParam);
            try {
                session.close(CloseStatus.BAD_DATA);
            } catch (IOException ex) {
                log.debug("Error closing session", ex);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        hub.removeSession(session);
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isBlank()) {
            return params;
        }
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
            params.put(key, value);
        }
        return params;
    }
}
