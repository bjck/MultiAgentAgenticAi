package com.bko.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
@Slf4j
public class RestClientConfig {

    private static final org.slf4j.Logger httpLogger = org.slf4j.LoggerFactory.getLogger("com.bko.http.logging");

    @Bean
    public RestClientCustomizer restClientCustomizer() {
        return restClientBuilder -> {
            restClientBuilder.requestInterceptor(new LoggingRequestInterceptor());
            // BufferingClientHttpRequestFactory allows multiple reads of the response body
            restClientBuilder.requestFactory(new BufferingClientHttpRequestFactory(new SimpleClientHttpRequestFactory()));
        };
    }

    private static class LoggingRequestInterceptor implements ClientHttpRequestInterceptor {
        private static final org.slf4j.Logger httpLogger = org.slf4j.LoggerFactory.getLogger("com.bko.http.logging");

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
            // Normalize Authorization header for providers that require an empty bearer token
            var headers = request.getHeaders();
            String auth = headers.getFirst("Authorization");
            if (auth != null) {
                String trimmed = auth.trim();
                if (trimmed.equalsIgnoreCase("Bearer none") || trimmed.equalsIgnoreCase("Bearer EMPTY")) {
                    headers.set("Authorization", "");
                }
            }

            logRequest(request, body);
            ClientHttpResponse response = execution.execute(request, body);
            logResponse(response);
            return response;
        }

        private void logRequest(HttpRequest request, byte[] body) {
            httpLogger.info("--- HTTP Request ---");
            httpLogger.info("URI: {} {}", request.getMethod(), request.getURI());
            httpLogger.info("Headers: {}", request.getHeaders());
            if (body.length > 0) {
                httpLogger.info("Body: {}", new String(body, StandardCharsets.UTF_8));
            }
            httpLogger.info("--------------------");
        }

        private void logResponse(ClientHttpResponse response) throws IOException {
            httpLogger.info("--- HTTP Response ---");
            try {
                httpLogger.info("Status: {}", response.getStatusCode());
            } catch (IOException e) {
                httpLogger.info("Status: Unknown");
            }
            httpLogger.info("Headers: {}", response.getHeaders());
            byte[] body = StreamUtils.copyToByteArray(response.getBody());
            if (body.length > 0) {
                httpLogger.info("Body: {}", new String(body, StandardCharsets.UTF_8));
            }
            httpLogger.info("---------------------");
        }
    }
}
