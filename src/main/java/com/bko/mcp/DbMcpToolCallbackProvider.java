package com.bko.mcp;

import com.bko.orchestration.service.McpServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.List;

@Slf4j
public class DbMcpToolCallbackProvider implements ToolCallbackProvider {

    private final McpServerService serverService;
    private volatile long lastVersion = -1;
    private volatile ToolCallbackProvider delegate;
    private static final ToolCallbackProvider EMPTY = () -> new ToolCallback[0];

    public DbMcpToolCallbackProvider(McpServerService serverService) {
        this.serverService = serverService;
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        ensureDelegate();
        return delegate != null ? delegate.getToolCallbacks() : new ToolCallback[0];
    }

    private synchronized void ensureDelegate() {
        long version = serverService.getVersion();
        if (delegate != null && lastVersion == version) {
            return;
        }
        List<?> clients = serverService.getClients();
        if (clients == null || clients.isEmpty()) {
            delegate = EMPTY;
            lastVersion = version;
            return;
        }
        try {
            delegate = new SyncMcpToolCallbackProvider(serverService.getClients());
        } catch (Exception ex) {
            log.warn("Failed to build MCP tool callbacks: {}", ex.getMessage());
            delegate = EMPTY;
        }
        lastVersion = version;
    }
}
