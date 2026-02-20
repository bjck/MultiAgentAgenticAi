package com.bko.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class OrchestratorConfig {

    @Bean
    @Primary
    public ChatClient chatClient(GoogleGenAiChatModel googleGenAiChatModel) {
        return ChatClient.builder(googleGenAiChatModel).build();
    }

    @Bean
    public ChatClient openAiChatClient(ObjectProvider<OpenAiChatModel> openAiChatModelProvider) {
        return openAiChatModelProvider.getIfAvailable() != null 
                ? ChatClient.builder(openAiChatModelProvider.getIfAvailable()).build() 
                : null;
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService workerExecutor(MultiAgentProperties properties) {
        return Executors.newFixedThreadPool(properties.getWorkerConcurrency());
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService orchestrationExecutor() {
        return Executors.newCachedThreadPool();
    }
}
