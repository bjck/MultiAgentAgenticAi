package com.bko.api;

import com.bko.entity.McpServer;
import com.bko.entity.McpTransportType;
import com.bko.orchestration.service.McpServerService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/config/mcp-servers")
public class McpServerController {

    private final McpServerService mcpServerService;

    public McpServerController(McpServerService mcpServerService) {
        this.mcpServerService = mcpServerService;
    }

    @GetMapping
    public List<McpServerView> listServers() {
        return mcpServerService.listServers().stream()
                .map(McpServerView::from)
                .toList();
    }

    @PutMapping
    public List<McpServerView> updateServers(@RequestBody McpServerUpdateRequest request) {
        List<McpServerService.McpServerUpdate> updates = request != null ? request.servers() : List.of();
        return mcpServerService.updateServers(updates).stream()
                .map(McpServerView::from)
                .toList();
    }

    public record McpServerUpdateRequest(List<McpServerService.McpServerUpdate> servers) {}

    public record McpServerView(String name,
                                McpTransportType transport,
                                String endpointUrl,
                                String headersJson,
                                boolean enabled) {
        public static McpServerView from(McpServer server) {
            return new McpServerView(
                    server.getName(),
                    server.getTransport(),
                    server.getEndpointUrl(),
                    server.getHeadersJson(),
                    server.isEnabled()
            );
        }
    }
}
