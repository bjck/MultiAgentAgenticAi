package com.bko;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class MockTestConfig {

    @Bean
    @Primary
    public ToolCallbackProvider toolCallbackProvider() {
        return mock(ToolCallbackProvider.class);
    }
}
