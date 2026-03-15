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
import java.time.Duration;

@Configuration
@Slf4j
public class RestClientConfig {

    /** Connect timeout for AI provider HTTP calls (e.g. Gemini, OpenAI). */
    private static final int CONNECT_TIMEOUT_SECONDS = 30;
    /** Read timeout for AI provider HTTP calls; should be >= worker-timeout for long tool-calling flows. */
    private static final int READ_TIMEOUT_SECONDS = 120;

    @Bean
    public RestClientCustomizer restClientCustomizer() {
        return restClientBuilder -> {
            restClientBuilder.requestInterceptor(new LoggingRequestInterceptor());
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS));
            factory.setReadTimeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS));
            restClientBuilder.requestFactory(new BufferingClientHttpRequestFactory(factory));
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
            if (!httpLogger.isDebugEnabled()) {
                return;
            }
            httpLogger.debug("HTTP request: {} {}", request.getMethod(), request.getURI());
            if (httpLogger.isTraceEnabled()) {
                httpLogger.trace("Request headers: {}", request.getHeaders());
                if (body.length > 0) {
                    httpLogger.trace("Request body: {}", new String(body, StandardCharsets.UTF_8));
                }
            }
        }

        private void logResponse(ClientHttpResponse response) throws IOException {
            if (!httpLogger.isDebugEnabled()) {
                return;
            }
            try {
                httpLogger.debug("HTTP response status: {}", response.getStatusCode());
            } catch (IOException e) {
                httpLogger.debug("HTTP response status: Unknown");
            }
            if (httpLogger.isTraceEnabled()) {
                httpLogger.trace("Response headers: {}", response.getHeaders());
                byte[] body = StreamUtils.copyToByteArray(response.getBody());
                if (body.length > 0) {
                    httpLogger.trace("Response body: {}", new String(body, StandardCharsets.UTF_8));
                }
            }
        }
    }
}
