package com.bko.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class OrchestratorConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService workerExecutor(MultiAgentProperties properties) {
        return Executors.newFixedThreadPool(properties.getWorkerConcurrency());
    }
}
