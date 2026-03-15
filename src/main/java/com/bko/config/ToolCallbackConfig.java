package com.bko.config;

import com.bko.mcp.DbMcpToolCallbackProvider;
import com.bko.orchestration.service.CompositeToolCallbackProvider;
import com.bko.orchestration.service.McpServerService;
import com.bko.tools.ArxivApiReaderTool;
import com.bko.tools.ExternalDocumentSearchTool;
import com.bko.tools.HttpFetchTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

@Configuration
public class ToolCallbackConfig {

    @Bean
    @Primary
    public ToolCallbackProvider toolCallbackProvider(McpServerService mcpServerService,
                                                     HttpFetchTool httpFetchTool,
                                                     ArxivApiReaderTool arxivApiReaderTool,
                                                     ExternalDocumentSearchTool externalDocumentSearchTool) {
        ToolCallbackProvider localProvider = MethodToolCallbackProvider.builder()
                .toolObjects(httpFetchTool, arxivApiReaderTool, externalDocumentSearchTool)
                .build();
        ToolCallbackProvider mcpProvider = new DbMcpToolCallbackProvider(mcpServerService);
        return new CompositeToolCallbackProvider(List.of(localProvider, mcpProvider));
    }
}
