package com.bko.orchestration.service;

import com.bko.files.FileEntry;
import com.bko.files.FileListing;
import com.bko.files.FileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Slf4j
final class AuditedToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final ToolCallAudit audit;
    private final FileService fileService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    AuditedToolCallback(ToolCallback delegate, ToolCallAudit audit, FileService fileService) {
        this.delegate = delegate;
        this.audit = audit;
        this.fileService = fileService;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String input) {
        return executeWithAudit(input, () -> delegate.call(input));
    }

    @Override
    public String call(String input, ToolContext toolContext) {
        return executeWithAudit(input, () -> delegate.call(input, toolContext));
    }

    private String resolveToolName() {
        String fromDef = reflectName(delegate.getToolDefinition());
        if (StringUtils.hasText(fromDef)) {
            return fromDef;
        }
        String fromMeta = reflectName(delegate.getToolMetadata());
        if (StringUtils.hasText(fromMeta)) {
            return fromMeta;
        }
        ToolDefinition def = delegate.getToolDefinition();
        return def != null ? def.toString() : "unknown";
    }

    private String executeWithAudit(String input, java.util.concurrent.Callable<String> call) {
        String toolName = resolveToolName();
        try {
            String output = call.call();
            audit.recordCall(toolName, input, output);
            return output;
        } catch (Exception ex) {
            String fallback = attemptReadFallback(toolName, input, ex);
            if (fallback != null) {
                audit.recordCall(toolName, input, fallback);
                return fallback;
            }
            fallback = attemptWriteFallback(toolName, input, ex);
            if (fallback != null) {
                audit.recordCall(toolName, input, fallback);
                return fallback;
            }
            if (ex instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new RuntimeException(ex);
        }
    }

    private String attemptWriteFallback(String toolName, String input, Exception ex) {
        if (!isWriteTool(toolName)) {
            return null;
        }
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        if (!message.toLowerCase(Locale.ROOT).contains("parent directory does not exist")) {
            return null;
        }
        try {
            String path = extractPathFromInput(input);
            if (!StringUtils.hasText(path)) {
                return null;
            }
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(input);
            String content = node.hasNonNull("content") ? node.get("content").asText() : "";
            fileService.write(path, content);
            log.warn("write_file fallback succeeded by creating parent directories. path={}", path);
            return fallbackJsonResponse("Fallback write_file: created parent directories and wrote file.");
        } catch (Exception fallbackEx) {
            log.warn("write_file fallback failed: {}", fallbackEx.getMessage());
            return null;
        }
    }

    private String attemptReadFallback(String toolName, String input, Exception ex) {
        if (!isReadTool(toolName) && !isListTool(toolName)) {
            return null;
        }
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        String normalized = message.toLowerCase(Locale.ROOT);
        if (!(normalized.contains("no such file") || normalized.contains("not found") || normalized.contains("enoent"))) {
            return null;
        }
        String hint = "";
        try {
            String path = extractPathFromInput(input);
            if (!StringUtils.hasText(path)) {
                path = extractPathFromMessage(message);
            }
            if (StringUtils.hasText(path)) {
                hint = buildDirectoryHint(path);
            }
        } catch (Exception ignore) {
            // ignore hint failures
        }
        log.warn("Recoverable tool error for {}: {}", toolName, message);
        String combined = hint.isBlank() ? "Tool error: " + message : "Tool error: " + message + " " + hint;
        return fallbackJsonResponse(combined);
    }

    private String fallbackJsonResponse(String message) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode root = objectMapper.createObjectNode();
            com.fasterxml.jackson.databind.node.ArrayNode content = root.putArray("content");
            com.fasterxml.jackson.databind.node.ObjectNode text = content.addObject();
            text.put("type", "text");
            text.put("text", message);
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            return "{\"content\":[{\"type\":\"text\",\"text\":\"" + message.replace("\"", "\\\"") + "\"}]}";
        }
    }

    private boolean isWriteTool(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            return false;
        }
        String normalized = toolName.toLowerCase(Locale.ROOT);
        return matchesToolSuffix(normalized, "write_file");
    }

    private boolean isReadTool(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            return false;
        }
        String normalized = toolName.toLowerCase(Locale.ROOT);
        return matchesToolSuffix(normalized, "read_file");
    }

    private boolean isListTool(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            return false;
        }
        String normalized = toolName.toLowerCase(Locale.ROOT);
        return matchesToolSuffix(normalized, "list_directory");
    }

    private boolean matchesToolSuffix(String name, String tool) {
        if (tool.equals(name)) {
            return true;
        }
        return name.endsWith("." + tool)
                || name.endsWith("/" + tool)
                || name.endsWith(":" + tool);
    }

    private String extractPathFromInput(String input) {
        if (!StringUtils.hasText(input)) {
            return "";
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(input);
            if (node.hasNonNull("path")) {
                return node.get("path").asText();
            }
            if (node.hasNonNull("file_path")) {
                return node.get("file_path").asText();
            }
            if (node.hasNonNull("filePath")) {
                return node.get("filePath").asText();
            }
        } catch (Exception ignore) {
            // ignore parse errors
        }
        return "";
    }

    private String extractPathFromMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return "";
        }
        String[] markers = {"open '", "open \"", "exist: ", "file not found: "};
        for (String marker : markers) {
            int start = message.toLowerCase(Locale.ROOT).indexOf(marker);
            if (start >= 0) {
                int begin = start + marker.length();
                int end = message.indexOf('\'', begin);
                if (end < 0) {
                    end = message.indexOf('"', begin);
                }
                if (end < 0) {
                    end = message.length();
                }
                return message.substring(begin, end).trim();
            }
        }
        return "";
    }

    private String buildDirectoryHint(String path) {
        try {
            java.nio.file.Path parent = java.nio.file.Paths.get(path).getParent();
            String parentPath = parent == null ? "" : parent.toString();
            FileListing listing = fileService.list(parentPath);
            List<FileEntry> entries = listing.entries();
            if (entries == null || entries.isEmpty()) {
                return "Directory is empty.";
            }
            String names = entries.stream()
                    .limit(20)
                    .map(FileEntry::name)
                    .toList()
                    .toString();
            return "Directory entries: " + names;
        } catch (Exception ignore) {
            return "";
        }
    }

    private String reflectName(@Nullable Object target) {
        if (target == null) {
            return "";
        }
        for (String method : java.util.List.of("getName", "name", "id")) {
            try {
                java.lang.reflect.Method m = target.getClass().getMethod(method);
                Object value = m.invoke(target);
                if (value instanceof String name && StringUtils.hasText(name)) {
                    return name;
                }
            } catch (Exception ignore) {
                // ignore reflection failures
            }
        }
        return "";
    }
}
