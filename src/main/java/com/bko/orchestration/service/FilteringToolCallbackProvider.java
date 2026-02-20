package com.bko.orchestration.service;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A ToolCallbackProvider wrapper that filters the delegate provider's callbacks by allowed tool names.
 * Name matching is case-insensitive. If the allowed set is empty, provides no tools.
 */
public class FilteringToolCallbackProvider implements ToolCallbackProvider {

    private static final Logger log = LoggerFactory.getLogger(FilteringToolCallbackProvider.class);
    private final ToolCallbackProvider delegate;
    private final Set<String> allowedNames;

    public FilteringToolCallbackProvider(ToolCallbackProvider delegate, List<String> allowedNames) {
        this.delegate = delegate;
        this.allowedNames = allowedNames == null ? Set.of() : allowedNames.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        if (delegate == null) return new ToolCallback[0];
        ToolCallback[] callbacks = delegate.getToolCallbacks();
        if (callbacks == null || callbacks.length == 0) return new ToolCallback[0];
        if (allowedNames.isEmpty()) return new ToolCallback[0];
        ToolCallback[] filtered = java.util.Arrays.stream(callbacks)
                .filter(this::isAllowed)
                .toArray(ToolCallback[]::new);
        if (filtered.length == 0 && log.isDebugEnabled()) {
            log.debug("Tool filtering removed all callbacks. allowed={}, available={}",
                    allowedNames, describeCallbacks(callbacks));
        }
        return filtered;
    }

    private boolean isAllowed(ToolCallback cb) {
        String name = extractName(cb);
        if (!StringUtils.hasText(name)) return false;
        if (allowedNames.contains(name)) return true;
        String stripped = stripPrefix(name);
        if (allowedNames.contains(stripped)) return true;
        for (String allowed : allowedNames) {
            if (name.endsWith("." + allowed) || name.endsWith("/" + allowed) || name.endsWith(":" + allowed)) {
                return true;
            }
        }
        return false;
    }

    private String extractName(@Nullable ToolCallback cb) {
        if (cb == null) return "";
        String fromDef = extractFromDefinition(cb.getToolDefinition());
        if (!fromDef.isBlank()) return fromDef;
        String fromMeta = extractFromMetadata(cb.getToolMetadata());
        if (!fromMeta.isBlank()) return fromMeta;
        try {
            Method m = cb.getClass().getMethod("getName");
            Object v = m.invoke(cb);
            if (v != null) return v.toString().toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {}
        try {
            Method m = cb.getClass().getMethod("name");
            Object v = m.invoke(cb);
            if (v != null) return v.toString().toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {}
        String s = cb.toString();
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private String extractFromDefinition(@Nullable ToolDefinition def) {
        if (def == null) return "";
        for (String method : List.of("getName", "name", "id")) {
            try {
                Method m = def.getClass().getMethod(method);
                Object v = m.invoke(def);
                if (v instanceof String name && !name.isBlank()) {
                    return name.trim().toLowerCase(Locale.ROOT);
                }
            } catch (Exception ignored) {}
        }
        return "";
    }

    private String extractFromMetadata(@Nullable ToolMetadata meta) {
        if (meta == null) return "";
        for (String method : List.of("getName", "name", "id")) {
            try {
                Method m = meta.getClass().getMethod(method);
                Object v = m.invoke(meta);
                if (v instanceof String name && !name.isBlank()) {
                    return name.trim().toLowerCase(Locale.ROOT);
                }
            } catch (Exception ignored) {}
        }
        return "";
    }

    private String stripPrefix(String name) {
        int dot = name.lastIndexOf('.');
        int slash = name.lastIndexOf('/');
        int colon = name.lastIndexOf(':');
        int idx = Math.max(dot, Math.max(slash, colon));
        if (idx < 0 || idx + 1 >= name.length()) {
            return name;
        }
        return name.substring(idx + 1);
    }

    private List<String> describeCallbacks(ToolCallback[] callbacks) {
        if (callbacks == null) return List.of();
        List<String> names = new java.util.ArrayList<>();
        for (ToolCallback cb : callbacks) {
            String name = extractName(cb);
            if (!name.isBlank()) names.add(name);
        }
        return names;
    }
}
