package com.bko.orchestration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bko.orchestration.model.OrchestratorPlan;
import com.bko.orchestration.model.WorkerResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JsonProcessingService {

    private final ObjectMapper objectMapper;

    public <T> @Nullable T parseJsonResponse(String label, @Nullable String raw, Class<T> type) {
        if (!StringUtils.hasText(raw)) {
            log.warn("Empty response for {}. Unable to parse JSON.", label);
            return null;
        }
        String json = extractJsonObject(raw);
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            log.warn("Failed to parse {} response as JSON. Snippet: {}", label, truncate(raw, 240));
            return null;
        }
    }

    private String extractJsonObject(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        }
        return trimmed;
    }

    private String truncate(String value, int maxLength) {
        String normalized = value.replace("\r", " ").replace("\n", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "\"serialization-failed-" + UUID.randomUUID() + "\"";
        }
    }
}
