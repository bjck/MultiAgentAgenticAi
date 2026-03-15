package com.bko.orchestration.service;

import com.bko.entity.McpServer;
import com.bko.entity.McpTransportType;
import com.bko.repository.McpServerRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
@Slf4j
public class McpServerService {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private final McpServerRepository repository;
    private final ObjectMapper objectMapper;

    private final AtomicReference<List<McpSyncClient>> clients = new AtomicReference<>(List.of());
    private final AtomicLong version = new AtomicLong(0);

    @PostConstruct
    public void init() {
        refreshClients();
    }

    public long getVersion() {
        return version.get();
    }

    public List<McpSyncClient> getClients() {
        return clients.get();
    }

    public List<McpServer> listServers() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(server -> server.getName() != null ? server.getName().toLowerCase(Locale.ROOT) : ""))
                .toList();
    }

    @Transactional
    public List<McpServer> updateServers(List<McpServerUpdate> updates) {
        if (updates == null || updates.isEmpty()) {
            return listServers();
        }
        Map<String, McpServer> byName = new HashMap<>();
        for (McpServer server : repository.findAll()) {
            if (server.getName() != null) {
                byName.put(server.getName().toLowerCase(Locale.ROOT), server);
            }
        }
        Set<String> seen = new HashSet<>();
        for (McpServerUpdate update : updates) {
            if (update == null || !StringUtils.hasText(update.name()) || !StringUtils.hasText(update.endpointUrl())) {
                continue;
            }
            String key = update.name().trim().toLowerCase(Locale.ROOT);
            McpServer server = byName.getOrDefault(key, new McpServer());
            server.setName(update.name().trim());
            server.setTransport(update.transport() != null ? update.transport() : McpTransportType.SSE);
            server.setEndpointUrl(update.endpointUrl().trim());
            server.setHeadersJson(StringUtils.hasText(update.headersJson()) ? update.headersJson().trim() : null);
            server.setEnabled(update.enabled());
            repository.save(server);
            seen.add(key);
        }
        for (McpServer server : byName.values()) {
            if (server.getName() == null) {
                continue;
            }
            String key = server.getName().toLowerCase(Locale.ROOT);
            if (!seen.contains(key) && server.isEnabled()) {
                server.setEnabled(false);
                repository.save(server);
            }
        }
        refreshClients();
        return listServers();
    }

    public void refreshClients() {
        List<McpSyncClient> previous = clients.getAndSet(List.of());
        for (McpSyncClient client : previous) {
            try {
                client.closeGracefully();
            } catch (Exception ex) {
                log.debug("Failed to close MCP client: {}", ex.getMessage());
            }
        }
        List<McpServer> enabled = repository.findEnabled();
        List<McpSyncClient> fresh = new ArrayList<>(enabled.size());
        for (McpServer server : enabled) {
            try {
                McpSyncClient client = buildClient(server);
                if (client != null) {
                    fresh.add(client);
                }
            } catch (Exception ex) {
                log.warn("Failed to initialize MCP server '{}': {}", server.getName(), ex.getMessage());
            }
        }
        clients.set(List.copyOf(fresh));
        version.incrementAndGet();
    }

    private McpSyncClient buildClient(McpServer server) {
        if (server == null || !StringUtils.hasText(server.getEndpointUrl()) || server.getTransport() == null) {
            return null;
        }
        McpClientTransport transport = switch (server.getTransport()) {
            case SSE -> buildSseTransport(server);
            case STREAMABLE_HTTP -> buildStreamableTransport(server);
        };
        if (transport == null) {
            return null;
        }
        return McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(60))
                .initializationTimeout(Duration.ofSeconds(20))
                .build();
    }

    private McpClientTransport buildSseTransport(McpServer server) {
        URI endpoint = URI.create(server.getEndpointUrl().trim());
        String baseUri = endpoint.getScheme() + "://" + endpoint.getHost()
                + (endpoint.getPort() > 0 ? ":" + endpoint.getPort() : "");
        String path = StringUtils.hasText(endpoint.getRawPath()) ? endpoint.getRawPath() : "/sse";
        HttpClientSseClientTransport.Builder builder = HttpClientSseClientTransport.builder(baseUri)
                .sseEndpoint(path)
                .connectTimeout(DEFAULT_CONNECT_TIMEOUT);
        applyHeaders(builder, server.getHeadersJson());
        return builder.build();
    }

    private McpClientTransport buildStreamableTransport(McpServer server) {
        HttpClientStreamableHttpTransport.Builder builder = HttpClientStreamableHttpTransport.builder(server.getEndpointUrl().trim())
                .connectTimeout(DEFAULT_CONNECT_TIMEOUT);
        applyHeaders(builder, server.getHeadersJson());
        return builder.build();
    }

    private void applyHeaders(HttpClientSseClientTransport.Builder builder, String headersJson) {
        if (!StringUtils.hasText(headersJson)) {
            return;
        }
        Map<String, String> headers = parseHeaders(headersJson);
        if (headers.isEmpty()) {
            return;
        }
        builder.customizeRequest(request -> headers.forEach(request::header));
    }

    private void applyHeaders(HttpClientStreamableHttpTransport.Builder builder, String headersJson) {
        if (!StringUtils.hasText(headersJson)) {
            return;
        }
        Map<String, String> headers = parseHeaders(headersJson);
        if (headers.isEmpty()) {
            return;
        }
        builder.customizeRequest(request -> headers.forEach(request::header));
    }

    private Map<String, String> parseHeaders(String headersJson) {
        try {
            return objectMapper.readValue(headersJson, new TypeReference<Map<String, String>>() {});
        } catch (Exception ex) {
            log.warn("Invalid MCP headers JSON; ignoring. {}", ex.getMessage());
            return Map.of();
        }
    }

    public record McpServerUpdate(String name,
                                  McpTransportType transport,
                                  String endpointUrl,
                                  String headersJson,
                                  boolean enabled) {
    }
}
