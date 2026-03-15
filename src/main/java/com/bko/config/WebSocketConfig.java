package com.bko.config;

import com.bko.stream.AgentRunUpdatesWebSocketHandler;
import com.bko.stream.OrchestrationStreamWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final OrchestrationStreamWebSocketHandler streamWebSocketHandler;
    private final AgentRunUpdatesWebSocketHandler agentRunUpdatesWebSocketHandler;

    public WebSocketConfig(OrchestrationStreamWebSocketHandler streamWebSocketHandler,
                          AgentRunUpdatesWebSocketHandler agentRunUpdatesWebSocketHandler) {
        this.streamWebSocketHandler = streamWebSocketHandler;
        this.agentRunUpdatesWebSocketHandler = agentRunUpdatesWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(streamWebSocketHandler, "/ws/stream")
                .setAllowedOrigins("*");
        registry.addHandler(agentRunUpdatesWebSocketHandler, "/ws/agent-runs")
                .setAllowedOrigins("*");
    }
}
